package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import java.lang.reflect.Field;
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
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(24454, 40.0, "token-secret", GameState::new);
        adapter.start();
        PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());

        FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(
                adapter.createConnectHandoff(playerReference));

        assertTrue(handoff.port() > 0);
        assertEquals(playerReference.value(), handoff.playerId());
        assertEquals(Long.BYTES + 32, handoff.token().length);

        adapter.stop();
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
}
