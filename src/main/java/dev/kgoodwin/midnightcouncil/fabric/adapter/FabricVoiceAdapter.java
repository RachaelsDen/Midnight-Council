package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.voice.VoiceProximityRouter;
import dev.kgoodwin.midnightcouncil.voice.VoiceTransport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricVoiceAdapter {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final double DEFAULT_VOICE_DISTANCE = 40.0;
    public static final String VOICE_CONNECT_CHANNEL = "voice.connect";

    private static final int CONNECT_TOKEN_LENGTH = Long.BYTES + 32;

    private static final Logger LOGGER = LoggerFactory.getLogger("midnightcouncil");

    private final VoiceTransport voiceServer;
    private final int voicePort;
    private volatile WorldAdapter worldAdapter;

    public FabricVoiceAdapter(int voicePort, double voiceDistance, String connectTokenSecret,
            Supplier<GameState> gameStateSupplier) {
        this.voicePort = voicePort;
        this.voiceServer = new VoiceTransport(
                new VoiceProximityRouter(voiceDistance),
                Objects.requireNonNull(gameStateSupplier, "gameStateSupplier"),
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

    public byte[] createConnectHandoff(PlayerReference playerReference) {
        Objects.requireNonNull(playerReference, "playerReference");
        int boundPort = voiceServer.getBoundPort();
        if (boundPort <= 0) {
            throw new IllegalStateException("Voice server is not bound to a connectable UDP port");
        }
        return serializeConnectHandoff(
                new VoiceConnectHandoff(boundPort, playerReference.value(), voiceServer.createConnectToken(playerReference)));
    }

    public void bindWorldAdapter(WorldAdapter worldAdapter) {
        this.worldAdapter = Objects.requireNonNull(worldAdapter, "worldAdapter");
        voiceServer.setInitialPositionProvider(playerReference -> this.worldAdapter.getPlayerPosition(playerReference).orElse(null));
    }

    public void syncPlayerPositions() {
        WorldAdapter currentWorldAdapter = worldAdapter;
        if (currentWorldAdapter == null) {
            return;
        }
        for (VoiceClientConnection connection : voiceServer.getConnections()) {
            PlayerReference playerReference = connection.getPlayerId();
            currentWorldAdapter.getPlayerPosition(playerReference)
                    .ifPresent(position -> voiceServer.updatePlayerPosition(playerReference, position));
        }
    }

    public boolean isVoiceRunning() {
        return voiceServer.isRunning();
    }

    public int getConnectionCount() {
        return voiceServer.getConnections().size();
    }

    public static VoiceConnectHandoff decodeConnectHandoff(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int port = input.readInt();
            int playerIdLength = input.readInt();
            if (playerIdLength <= 0 || input.available() < playerIdLength + CONNECT_TOKEN_LENGTH) {
                throw new IllegalArgumentException("Invalid voice connect handoff payload");
            }
            String playerId = new String(input.readNBytes(playerIdLength), StandardCharsets.UTF_8);
            byte[] token = input.readNBytes(CONNECT_TOKEN_LENGTH);
            if (token.length != CONNECT_TOKEN_LENGTH || input.available() != 0) {
                throw new IllegalArgumentException("Invalid voice connect handoff payload");
            }
            return new VoiceConnectHandoff(port, playerId, token);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid voice connect handoff payload", e);
        }
    }

    public record VoiceConnectHandoff(int port, String playerId, byte[] token) {

        public VoiceConnectHandoff {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 0 and 65535");
            }
            Objects.requireNonNull(playerId, "playerId");
            if (playerId.isBlank()) {
                throw new IllegalArgumentException("playerId cannot be blank");
            }
            Objects.requireNonNull(token, "token");
            if (token.length != CONNECT_TOKEN_LENGTH) {
                throw new IllegalArgumentException("token must be exactly " + CONNECT_TOKEN_LENGTH + " bytes");
            }
            token = token.clone();
        }

        @Override
        public byte[] token() {
            return token.clone();
        }
    }

    private static String requireConnectTokenSecret(String connectTokenSecret) {
        if (connectTokenSecret == null || connectTokenSecret.isBlank()) {
            throw new IllegalArgumentException("connectTokenSecret cannot be blank");
        }
        return connectTokenSecret;
    }

    private static byte[] serializeConnectHandoff(VoiceConnectHandoff handoff) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            byte[] playerIdBytes = handoff.playerId().getBytes(StandardCharsets.UTF_8);
            output.writeInt(handoff.port());
            output.writeInt(playerIdBytes.length);
            output.write(playerIdBytes);
            output.write(handoff.token());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize voice connect handoff", e);
        }
    }
}
