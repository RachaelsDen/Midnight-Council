package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.voice.VoiceProximityRouter;
import dev.kgoodwin.midnightcouncil.voice.VoiceTransport;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricVoiceAdapter {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final double DEFAULT_VOICE_DISTANCE = 40.0;

    private static final Logger LOGGER = LoggerFactory.getLogger("midnightcouncil");

    private final VoiceTransport voiceServer;
    private final int voicePort;

    public FabricVoiceAdapter(int voicePort, double voiceDistance, String connectTokenSecret) {
        this.voicePort = voicePort;
        this.voiceServer = new VoiceTransport(
                new VoiceProximityRouter(voiceDistance),
                GameState::new,
                requireConnectTokenSecret(connectTokenSecret));
    }

    public void start() {
        if (voiceServer.isRunning()) {
            return;
        }
        voiceServer.start(voicePort);
        LOGGER.info("Voice server started on UDP port {}", voicePort);
    }

    public void stop() {
        if (!voiceServer.isRunning()) {
            return;
        }
        voiceServer.stop();
        LOGGER.info("Voice server stopped");
    }

    public void disconnectPlayer(PlayerReference playerReference) {
        Objects.requireNonNull(playerReference, "playerReference");
        for (VoiceClientConnection connection : voiceServer.getConnections()) {
            if (playerReference.equals(connection.getPlayerId())) {
                voiceServer.disconnect(connection);
            }
        }
    }

    public boolean isVoiceRunning() {
        return voiceServer.isRunning();
    }

    public int getConnectionCount() {
        return voiceServer.getConnections().size();
    }

    private static String requireConnectTokenSecret(String connectTokenSecret) {
        if (connectTokenSecret == null || connectTokenSecret.isBlank()) {
            throw new IllegalArgumentException("connectTokenSecret cannot be blank");
        }
        return connectTokenSecret;
    }
}
