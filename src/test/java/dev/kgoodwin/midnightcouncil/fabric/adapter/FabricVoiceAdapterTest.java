package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.voice.VoiceTransport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class FabricVoiceAdapterTest {

    @Test
    void startAndStopToggleVoiceServerLifecycle() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", GameState::new);

        adapter.start();
        assertTrue(adapter.isVoiceRunning());

        adapter.stop();
        assertFalse(adapter.isVoiceRunning());
    }

    @Test
    void disconnectPlayerIsSafeWhenNoConnectionExists() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", GameState::new);

        assertDoesNotThrow(() -> adapter.disconnectPlayer(PlayerReference.ofName("alice")));
    }

    @Test
    void createConnectHandoffEncodesBoundPortPlayerIdAndToken() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", GameState::new);
        try {
            adapter.start();
            PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());

            FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(
                    adapter.createConnectHandoff(playerReference));

            assertTrue(handoff.port() > 0);
            assertEquals(playerReference.value(), handoff.playerId());
            assertEquals(Long.BYTES + 32, handoff.token().length);
        } finally {
            adapter.stop();
        }
    }

    @Test
    void sameMillisecondReplacementKeepsReturnedTokenValid() throws ReflectiveOperationException {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", GameState::new);
        VoiceTransport voiceServer = voiceServer(adapter);
        PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());
        long issuedAt = 123456789L;

        byte[] firstToken = adapter.issuePendingConnectToken(
                playerReference,
                deterministicTokenSupplier(voiceServer, playerReference, issuedAt));
        byte[] secondToken = adapter.issuePendingConnectToken(
                playerReference,
                deterministicTokenSupplier(voiceServer, playerReference, issuedAt));

        assertArrayEquals(firstToken, secondToken);
        assertTrue(voiceServer.invalidateConnectToken(secondToken));
        assertFalse(voiceServer.invalidateConnectToken(secondToken));
    }

    @Test
    void usesSuppliedGameStateSupplier() throws ReflectiveOperationException {
        Supplier<GameState> supplier = GameState::new;
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", supplier);

        Field voiceServerField = FabricVoiceAdapter.class.getDeclaredField("voiceServer");
        voiceServerField.setAccessible(true);
        Object voiceServer = voiceServerField.get(adapter);
        Field supplierField = voiceServer.getClass().getDeclaredField("gameStateSupplier");
        supplierField.setAccessible(true);

        assertSame(supplier, supplierField.get(voiceServer));
    }

    @Test
    void syncPlayerPositionsIsSafeWithoutConnections() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret", GameState::new);
        adapter.bindWorldAdapter(mock(WorldAdapter.class));

        assertDoesNotThrow(adapter::syncPlayerPositions);
    }

    @Test
    void rejectsInvalidConnectHandoffPayload() {
        assertThrows(IllegalArgumentException.class, () -> FabricVoiceAdapter.decodeConnectHandoff(new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsOversizedPlayerIdLengthWithoutAllocating() {
        byte[] payload = ByteBuffer.allocate(Integer.BYTES * 2)
                .putInt(24454)
                .putInt(Integer.MAX_VALUE)
                .array();

        assertThrows(IllegalArgumentException.class, () -> FabricVoiceAdapter.decodeConnectHandoff(payload));
    }

    @Test
    void rejectsBlankConnectTokenSecret() {
        assertThrows(IllegalArgumentException.class, () -> new FabricVoiceAdapter(0, 40.0, " ", GameState::new));
    }

    private static VoiceTransport voiceServer(FabricVoiceAdapter adapter) throws ReflectiveOperationException {
        Field voiceServerField = FabricVoiceAdapter.class.getDeclaredField("voiceServer");
        voiceServerField.setAccessible(true);
        return (VoiceTransport) voiceServerField.get(adapter);
    }

    private static byte[] createConnectToken(VoiceTransport voiceServer, PlayerReference playerReference, long issuedAt)
            throws ReflectiveOperationException {
        Method method = VoiceTransport.class.getDeclaredMethod("createConnectToken", PlayerReference.class, long.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(voiceServer, playerReference, issuedAt);
    }

    private static Supplier<byte[]> deterministicTokenSupplier(
            VoiceTransport voiceServer,
            PlayerReference playerReference,
            long issuedAt) {
        return () -> {
            try {
                return createConnectToken(voiceServer, playerReference, issuedAt);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
