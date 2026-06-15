package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

public final class VoiceClientTransport implements AutoCloseable {

    static final long KEEPALIVE_INTERVAL_MS = 5_000L;

    private final DatagramSocket socket;
    private final InetSocketAddress serverAddress;
    private final PlayerReference playerId;
    private final SecretKey sessionKey;
    private final ScheduledExecutorService keepaliveExecutor;
    private final Thread receiveThread;
    private final AtomicLong sendSequence = new AtomicLong();
    private volatile VoicePacketHandler audioHandler;
    private volatile boolean closed;

    private VoiceClientTransport(
            DatagramSocket socket,
            InetSocketAddress serverAddress,
            PlayerReference playerId,
            SecretKey sessionKey) throws SocketException {
        this.socket = socket;
        this.serverAddress = serverAddress;
        this.playerId = playerId;
        this.sessionKey = sessionKey;
        this.socket.setSoTimeout(0);
        this.keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
        this.keepaliveExecutor.scheduleAtFixedRate(
                this::sendKeepaliveSafely,
                KEEPALIVE_INTERVAL_MS,
                KEEPALIVE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        this.receiveThread = new Thread(this::runReceiveLoop, "midnightcouncil-voice-receive");
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();
    }

    @FunctionalInterface
    public interface VoicePacketHandler {
        void handleAudio(AudioPacket packet);
    }

    public static VoiceClientTransport connect(
            InetAddress host,
            int port,
            PlayerReference playerId,
            byte[] connectToken,
            int timeoutMillis) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(connectToken, "connectToken");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }

        DatagramSocket socket = new DatagramSocket();
        boolean success = false;
        try {
            socket.setSoTimeout(timeoutMillis);
            socket.connect(host, port);
            KeyPair clientKeyPair = CryptoUtils.generateEcdhKeyPair();
            byte[] connectPacket = VoiceTransport.serializeConnectPacket(
                    playerId,
                    connectToken,
                    CryptoUtils.encodeEcdhPublicKey(clientKeyPair.getPublic()));
            socket.send(new DatagramPacket(connectPacket, connectPacket.length));

            byte[] ackBytes = receiveConnectAck(socket);
            SecretKey sessionKey = deriveSessionKey(clientKeyPair, ackBytes);
            VoiceClientTransport transport = new VoiceClientTransport(
                    socket,
                    new InetSocketAddress(host, port),
                    playerId,
                    sessionKey);
            success = true;
            return transport;
        } finally {
            if (!success) {
                socket.close();
            }
        }
    }

    public SecretKey sessionKey() {
        return sessionKey;
    }

    public PlayerReference playerId() {
        return playerId;
    }

    public synchronized void sendKeepalive() throws IOException {
        ensureOpen();
        long sequenceNumber = sendSequence.getAndIncrement();
        byte[] keepalive = VoiceTransport.serializeKeepalivePacket(sessionKey, sequenceNumber);
        socket.send(new DatagramPacket(keepalive, keepalive.length, serverAddress));
    }

    public synchronized void sendAudio(byte[] encodedData, long sequenceNumber, long timestamp) throws IOException {
        ensureOpen();
        long seq = sendSequence.getAndIncrement();
        AudioPacket packet = new AudioPacket(playerId, encodedData, sequenceNumber, timestamp);
        byte[] payload = VoiceTransport.serializeAudioPayload(packet);
        byte[] encrypted = CryptoUtils.encrypt(payload, sessionKey, seq, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
        byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
                .putLong(seq)
                .put(encrypted)
                .array();
        socket.send(new DatagramPacket(framed, framed.length, serverAddress));
    }

    public void setAudioHandler(VoicePacketHandler handler) {
        this.audioHandler = handler;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        keepaliveExecutor.shutdownNow();
        try {
            if (!socket.isClosed()) {
                long sequenceNumber = sendSequence.getAndIncrement();
                byte[] disconnectPayload = VoiceTransport.serializeDisconnectPacket();
                byte[] encrypted = CryptoUtils.encrypt(
                        disconnectPayload,
                        sessionKey,
                        sequenceNumber,
                        CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
                byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
                        .putLong(sequenceNumber)
                        .put(encrypted)
                        .array();
                socket.send(new DatagramPacket(framed, framed.length, serverAddress));
            }
        } catch (IOException ignored) {
        } finally {
            socket.close();
        }
    }

    private void runReceiveLoop() {
        byte[] buffer = new byte[2048];
        while (!closed && !socket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                handleDatagram(Arrays.copyOf(datagram.getData(), datagram.getLength()));
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (closed || socket.isClosed()) {
                    break;
                }
            } catch (RuntimeException e) {
                if (closed || socket.isClosed()) {
                    break;
                }
            }
        }
    }

    private void handleDatagram(byte[] datagramBytes) {
        if (datagramBytes.length <= Long.BYTES) {
            return;
        }
        ByteBuffer frame = ByteBuffer.wrap(datagramBytes);
        long sequenceNumber = frame.getLong();
        byte[] encrypted = new byte[datagramBytes.length - Long.BYTES];
        frame.get(encrypted);
        byte[] decrypted = CryptoUtils.decrypt(encrypted, sessionKey, sequenceNumber,
                CryptoUtils.DIRECTION_SERVER_TO_CLIENT);
        if (decrypted.length == 0) {
            return;
        }
        PacketType packetType = PacketType.fromIdSafe(decrypted[0]);
        if (packetType == null) {
            return;
        }
        if (packetType == PacketType.AUDIO) {
            AudioPacket packet = VoiceTransport.deserializeAudioPayload(decrypted);
            VoicePacketHandler handler = audioHandler;
            if (handler != null) {
                handler.handleAudio(packet);
            }
        }
    }

    private void sendKeepaliveSafely() {
        try {
            sendKeepalive();
        } catch (IOException e) {
            close();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed || socket.isClosed()) {
            throw new IOException("Voice client transport is closed");
        }
    }

    private static byte[] receiveConnectAck(DatagramSocket socket) throws IOException {
        byte[] buf = new byte[512];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        socket.receive(response);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(response.getData(), 0, response.getLength()))) {
            boolean success = input.readBoolean();
            if (!success) {
                throw new IOException("Voice server rejected CONNECT handshake");
            }
            byte[] serverPublicKey = input.readNBytes(CryptoUtils.X25519_PUBLIC_KEY_LENGTH);
            if (serverPublicKey.length != CryptoUtils.X25519_PUBLIC_KEY_LENGTH || input.available() != 0) {
                throw new IOException("Invalid CONNECT_ACK payload from voice server");
            }
            return serverPublicKey;
        }
    }

    private static SecretKey deriveSessionKey(KeyPair clientKeyPair, byte[] serverPublicKey) throws IOException {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
            keyAgreement.init(clientKeyPair.getPrivate());
            keyAgreement.doPhase(CryptoUtils.decodeEcdhPublicKey(serverPublicKey), true);
            return CryptoUtils.deriveSessionKey(keyAgreement.generateSecret());
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to derive UDP voice session key", e);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "midnightcouncil-voice-keepalive");
            thread.setDaemon(true);
            return thread;
        };
    }
}
