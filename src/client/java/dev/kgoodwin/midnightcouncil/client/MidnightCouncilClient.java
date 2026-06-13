package dev.kgoodwin.midnightcouncil.client;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilClient.class);

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MidnightCouncilPayload.TYPE, (payload, context) -> {
            if (FabricVoiceAdapter.VOICE_CONNECT_CHANNEL.equals(payload.channel())) {
                FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(payload.bytes());
                LOG.info("Received voice connect handoff for UDP port {}", handoff.port());
                return;
            }
            LOG.debug("Received Midnight Council payload channel={} ({} bytes)", payload.channel(), payload.bytes().length);
        });
    }
}
