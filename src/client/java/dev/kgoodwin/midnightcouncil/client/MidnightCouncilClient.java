package dev.kgoodwin.midnightcouncil.client;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilClient.class);
    private static final int VOICE_CONNECT_TIMEOUT_MS = 2_000;

    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "midnightcouncil-voice-client");
        thread.setDaemon(true);
        return thread;
    });
    private final Object voiceTransportLock = new Object();
    private final AtomicLong voiceSessionGeneration = new AtomicLong();
    private volatile VoiceClientTransport activeVoiceTransport;

    @Override
    public void onInitializeClient() {
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
            LOG.debug("Received Midnight Council payload channel={} ({} bytes)", payload.channel(), payload.bytes().length);
        });
    }

    private void queueVoiceConnect(
            SocketAddress remoteAddress,
            FabricVoiceAdapter.VoiceConnectHandoff handoff,
            long generation) {
        try {
            InetAddress voiceHost = resolveVoiceHost(remoteAddress);
            voiceExecutor.submit(() -> connectVoiceTransport(voiceHost, handoff, generation));
        } catch (IOException e) {
            LOG.warn("Unable to resolve voice server host for handoff on UDP port {}", handoff.port(), e);
        }
    }

    private void connectVoiceTransport(InetAddress voiceHost, FabricVoiceAdapter.VoiceConnectHandoff handoff, long generation) {
        try {
            VoiceClientTransport newTransport = VoiceClientTransport.connect(
                    voiceHost,
                    handoff.port(),
                    PlayerReference.ofName(handoff.playerId()),
                    handoff.token(),
                    VOICE_CONNECT_TIMEOUT_MS);
            if (!publishActiveVoiceTransport(generation, newTransport)) {
                return;
            }
            LOG.info("Started UDP voice session for player {} on {}:{}",
                    handoff.playerId(), voiceHost.getHostAddress(), handoff.port());
        } catch (IOException e) {
            LOG.warn("Failed to start UDP voice session for player {} on UDP port {}",
                    handoff.playerId(), handoff.port(), e);
        }
    }

    boolean publishActiveVoiceTransport(long generation, VoiceClientTransport newTransport) {
        VoiceClientTransport previousTransport;
        synchronized (voiceTransportLock) {
            if (generation != voiceSessionGeneration.get()) {
                newTransport.close();
                return false;
            }
            previousTransport = activeVoiceTransport;
            activeVoiceTransport = newTransport;
        }
        if (previousTransport != null) {
            previousTransport.close();
        }
        return true;
    }

    void clearActiveVoiceTransport() {
        VoiceClientTransport transport;
        synchronized (voiceTransportLock) {
            voiceSessionGeneration.incrementAndGet();
            transport = activeVoiceTransport;
            activeVoiceTransport = null;
        }
        if (transport != null) {
            transport.close();
        }
    }

    long currentVoiceSessionGeneration() {
        return voiceSessionGeneration.get();
    }

    VoiceClientTransport activeVoiceTransportForTest() {
        return activeVoiceTransport;
    }

    private static InetAddress resolveVoiceHost(SocketAddress remoteAddress) throws IOException {
        if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress();
            }
            return InetAddress.getByName(inetSocketAddress.getHostString());
        }
        throw new IOException("No live remote server address available for voice bootstrap");
    }
}
