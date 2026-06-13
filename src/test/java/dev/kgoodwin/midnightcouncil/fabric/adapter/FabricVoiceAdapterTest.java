package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import org.junit.jupiter.api.Test;

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
    void rejectsBlankConnectTokenSecret() {
        assertThrows(IllegalArgumentException.class, () -> new FabricVoiceAdapter(0, 40.0, " "));
    }
}
