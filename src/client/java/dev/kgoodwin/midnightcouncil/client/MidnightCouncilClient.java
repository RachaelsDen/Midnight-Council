package dev.kgoodwin.midnightcouncil.client;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.client.voice.VoiceAudioIO;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientService;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import dev.kgoodwin.midnightcouncil.voice.VoiceCodec;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilClient.class);
    private static final int VOICE_CONNECT_TIMEOUT_MS = 2_000;
    private static volatile MidnightCouncilClient instance;

    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "midnightcouncil-voice-client");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<String, Consumer<byte[]>> channelHandlers = new ConcurrentHashMap<>();
    private final Object voiceTransportLock = new Object();
    private final AtomicLong voiceSessionGeneration = new AtomicLong();
    private volatile VoiceClientTransport activeVoiceTransport;
    private volatile VoiceClientService activeVoiceService;
    private volatile VoiceAudioIO activeVoiceAudioIO;

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::clearActiveVoiceTransport));
        ClientPlayNetworking.registerGlobalReceiver(MidnightCouncilPayload.TYPE, (payload, context) -> {
            if (FabricVoiceAdapter.VOICE_CONNECT_CHANNEL.equals(payload.channel())) {
                FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(payload.bytes());
                long generation = currentVoiceSessionGeneration();
                SocketAddress remoteAddress = context.client().getConnection() != null
                        ? context.client().getConnection().getConnection().getRemoteAddress()
                        : null;
                context.client().execute(() -> queueVoiceConnect(remoteAddress, handoff, generation));
                return;
            }
            dispatchClientboundPayload(payload.channel(), payload.bytes());
        });
    }

    public static MidnightCouncilClient getInstance() {
        return instance;
    }

    public void registerChannelHandler(String channel, Consumer<byte[]> handler) {
        channelHandlers.put(channel, handler);
    }

    public void unregisterChannelHandler(String channel) {
        channelHandlers.remove(channel);
    }

    private void queueVoiceConnect(
            SocketAddress remoteAddress,
            FabricVoiceAdapter.VoiceConnectHandoff handoff,
            long generation) {
        try {
            InetAddress voiceHost = MidnightCouncilClient.resolveVoiceHost(remoteAddress);
            voiceExecutor.submit(() -> connectVoiceTransport(voiceHost, handoff, generation));
        } catch (IOException e) {
            LOG.warn("Unable to resolve voice server host for handoff on UDP port {}", handoff.port(), e);
        }
    }

    private void connectVoiceTransport(InetAddress voiceHost, FabricVoiceAdapter.VoiceConnectHandoff handoff, long generation) {
        VoiceClientTransport newTransport = null;
        try {
            newTransport = VoiceClientTransport.connect(
                    voiceHost,
                    handoff.port(),
                    PlayerReference.ofName(handoff.playerId()),
                    handoff.token(),
                    VOICE_CONNECT_TIMEOUT_MS);
            finishConnectedVoiceTransportSetup(voiceHost, handoff, generation, newTransport, this::createConnectedVoiceClientService);
            newTransport = null;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to start UDP voice session for player {} on UDP port {}",
                    handoff.playerId(), handoff.port(), e);
        } finally {
            if (newTransport != null) {
                newTransport.close();
            }
        }
    }

    void finishConnectedVoiceTransportSetup(
            InetAddress voiceHost,
            FabricVoiceAdapter.VoiceConnectHandoff handoff,
            long generation,
            VoiceClientTransport newTransport,
            Function<PlayerReference, VoiceClientService> serviceFactory) {
        VoiceAudioIO audioIO = null;
        try {
            PlayerReference playerId = PlayerReference.ofName(handoff.playerId());
            VoiceClientService newService = serviceFactory.apply(playerId);
            newTransport.setAudioHandler(newService::receiveAudio);
            if (!publishActiveVoiceTransport(generation, newTransport, newService)) {
                return;
            }
            audioIO = new VoiceAudioIO(newService, newTransport);
            audioIO.start();
            activeVoiceAudioIO = audioIO;
            audioIO = null;
            LOG.info("Started UDP voice session for player {} on {}:{}",
                    handoff.playerId(), voiceHost.getHostAddress(), handoff.port());
        } finally {
            if (activeVoiceTransportForTest() != newTransport) {
                newTransport.close();
            }
            if (audioIO != null) {
                audioIO.close();
            }
        }
    }

    private VoiceClientService createConnectedVoiceClientService(PlayerReference playerId) {
        VoiceClientService newService = new VoiceClientService(VoiceCodec.builder().build());
        newService.connect(playerId);
        return newService;
    }

    boolean publishActiveVoiceTransport(long generation, VoiceClientTransport newTransport, VoiceClientService newService) {
        VoiceClientTransport previousTransport;
        VoiceClientService previousService;
        VoiceAudioIO previousAudioIO;
        synchronized (voiceTransportLock) {
            if (generation != voiceSessionGeneration.get()) {
                disconnectVoiceService(newService);
                newTransport.close();
                return false;
            }
            previousTransport = activeVoiceTransport;
            previousService = activeVoiceService;
            previousAudioIO = activeVoiceAudioIO;
            activeVoiceTransport = newTransport;
            activeVoiceService = newService;
            activeVoiceAudioIO = null;
        }
        if (previousAudioIO != null) {
            previousAudioIO.close();
        }
        disconnectVoiceService(previousService);
        if (previousTransport != null) {
            previousTransport.close();
        }
        return true;
    }

    void clearActiveVoiceTransport() {
        VoiceClientTransport transport;
        VoiceClientService service;
        VoiceAudioIO audioIO;
        synchronized (voiceTransportLock) {
            voiceSessionGeneration.incrementAndGet();
            transport = activeVoiceTransport;
            service = activeVoiceService;
            audioIO = activeVoiceAudioIO;
            activeVoiceTransport = null;
            activeVoiceService = null;
            activeVoiceAudioIO = null;
        }
        if (audioIO != null) {
            audioIO.close();
        }
        disconnectVoiceService(service);
        if (transport != null) {
            transport.close();
        }
    }

    private static void disconnectVoiceService(VoiceClientService service) {
        if (service != null && service.isConnected()) {
            service.disconnect();
        }
    }

    void dispatchClientboundPayload(String channel, byte[] bytes) {
        Consumer<byte[]> handler = channelHandlers.get(channel);
        if (handler != null) {
            handler.accept(bytes);
            return;
        }
        LOG.debug("Received Midnight Council payload channel={} ({} bytes)", channel, bytes == null ? 0 : bytes.length);
    }

    long currentVoiceSessionGeneration() {
        return voiceSessionGeneration.get();
    }

    VoiceClientTransport activeVoiceTransportForTest() {
        return activeVoiceTransport;
    }

    VoiceClientService activeVoiceServiceForTest() {
        return activeVoiceService;
    }

    VoiceAudioIO activeVoiceAudioIOForTest() {
        return activeVoiceAudioIO;
    }

    static InetAddress resolveVoiceHost(SocketAddress remoteAddress) throws IOException {
        if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress();
            }
            return InetAddress.getByName(inetSocketAddress.getHostString());
        }
        return InetAddress.getLoopbackAddress();
    }
}
