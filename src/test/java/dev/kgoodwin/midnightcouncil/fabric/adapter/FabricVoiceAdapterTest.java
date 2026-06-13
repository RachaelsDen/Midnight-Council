package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class FabricVoiceAdapterTest {

    @Test
    void startAndStopToggleVoiceServerLifecycle() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret");

        adapter.start();
        assertTrue(adapter.isVoiceRunning());

        adapter.stop();
        assertFalse(adapter.isVoiceRunning());
    }

    @Test
    void disconnectPlayerIsSafeWhenNoConnectionExists() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret");

        assertDoesNotThrow(() -> adapter.disconnectPlayer(PlayerReference.ofName("alice")));
    }

    @Test
    void createConnectHandoffEncodesConfiguredPortAndToken() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(24454, 40.0, "token-secret");
        adapter.start();

        FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(
                adapter.createConnectHandoff(PlayerReference.ofName("alice")));

        assertTrue(handoff.port() > 0);
        assertEquals(Long.BYTES + 32, handoff.token().length);

        adapter.stop();
    }

    @Test
    void syncPlayerPositionsIsSafeWithoutConnections() {
        FabricVoiceAdapter adapter = new FabricVoiceAdapter(0, 40.0, "token-secret");
        adapter.bindWorldAdapter(mock(WorldAdapter.class));

        assertDoesNotThrow(adapter::syncPlayerPositions);
    }

    @Test
    void rejectsInvalidConnectHandoffPayload() {
        assertThrows(IllegalArgumentException.class, () -> FabricVoiceAdapter.decodeConnectHandoff(new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsBlankConnectTokenSecret() {
        assertThrows(IllegalArgumentException.class, () -> new FabricVoiceAdapter(0, 40.0, " "));
    }
}
