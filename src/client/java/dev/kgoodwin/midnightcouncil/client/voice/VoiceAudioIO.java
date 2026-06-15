package dev.kgoodwin.midnightcouncil.client.voice;

import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientService;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoiceAudioIO implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceAudioIO.class);

    private static final int SAMPLE_RATE = 48_000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int FRAME_SIZE = 960; // 20ms at 48kHz
    private static final int BYTES_PER_FRAME = FRAME_SIZE * 2; // 16-bit = 2 bytes per sample

    private final VoiceClientService service;
    private final VoiceClientTransport transport;
    private final AudioFormat format;

    private TargetDataLine microphoneLine;
    private SourceDataLine speakerLine;
    private Thread captureThread;
    private Thread playbackThread;
    private volatile boolean closed;

    public VoiceAudioIO(VoiceClientService service, VoiceClientTransport transport) {
        this.service = service;
        this.transport = transport;
        this.format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);
    }

    public void start() {
        try {
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphoneLine = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphoneLine.open(format, BYTES_PER_FRAME * 2);
            microphoneLine.start();
            LOG.info("Microphone opened successfully");

            captureThread = new Thread(this::runCaptureLoop, "midnightcouncil-mic-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (LineUnavailableException | SecurityException e) {
            LOG.warn("Microphone unavailable, voice capture disabled: {}", e.getMessage());
            microphoneLine = null;
        }

        try {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakerLine.open(format, BYTES_PER_FRAME * 4);
            speakerLine.start();
            LOG.info("Speaker opened successfully");

            playbackThread = new Thread(this::runPlaybackLoop, "midnightcouncil-speaker-playback");
            playbackThread.setDaemon(true);
            playbackThread.start();
        } catch (LineUnavailableException | SecurityException e) {
            LOG.warn("Speaker unavailable, voice playback disabled: {}", e.getMessage());
            speakerLine = null;
        }
    }

    private void runCaptureLoop() {
        byte[] buffer = new byte[BYTES_PER_FRAME];
        TargetDataLine mic = microphoneLine;
        while (!closed && mic != null && mic.isOpen()) {
            int read = mic.read(buffer, 0, buffer.length);
            if (read < buffer.length) {
                continue;
            }
            short[] pcm = bytesToShorts(buffer);
            AudioPacket packet;
            try {
                packet = service.sendAudio(pcm);
            } catch (Exception e) {
                LOG.debug("Failed to encode audio packet: {}", e.getMessage());
                continue;
            }
            if (packet == null) {
                continue;
            }
            try {
                transport.sendAudio(packet.encodedData(), packet.sequenceNumber(), packet.timestamp());
            } catch (Exception e) {
                LOG.debug("Failed to send audio packet: {}", e.getMessage());
            }
        }
    }

    private void runPlaybackLoop() {
        SourceDataLine speaker = speakerLine;
        while (!closed && speaker != null && speaker.isOpen()) {
            var pending = service.getPendingAudio();
            if (pending.isEmpty()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            for (short[] pcmFrame : pending) {
                if (closed) {
                    break;
                }
                byte[] bytes = shortsToBytes(pcmFrame);
                speaker.write(bytes, 0, bytes.length);
            }
            service.clearPendingAudio();
        }
    }

    static short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            // Little-endian: LSB first
            shorts[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
        }
        return shorts;
    }

    static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            // Little-endian: LSB first
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    boolean hasMicrophone() {
        return microphoneLine != null;
    }

    boolean hasSpeaker() {
        return speakerLine != null;
    }

    @Override
    public void close() {
        closed = true;
        if (microphoneLine != null) {
            microphoneLine.stop();
            microphoneLine.close();
            microphoneLine = null;
        }
        if (speakerLine != null) {
            speakerLine.stop();
            speakerLine.flush();
            speakerLine.close();
            speakerLine = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
    }
}
