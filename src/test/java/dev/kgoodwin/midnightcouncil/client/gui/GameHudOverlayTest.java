package dev.kgoodwin.midnightcouncil.client.gui;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameHudOverlayTest {

    @Test
    void formatPhaseDisplay_shouldFormatCorrectly() {
        assertEquals("Day: 2 | Night: 1", GameHudOverlay.formatPhaseDisplay(GamePhase.DAY, 2, 1));
        assertEquals("Day: 3 | Night: 3", GameHudOverlay.formatPhaseDisplay(GamePhase.NIGHT, 3, 3));
    }

    @Test
    void formatPlayerCountText_shouldCountOnlyAlivePlayers() {
        List<PlayerSnapshot> players = List.of(
            new PlayerSnapshot(1, "Player1", LifeState.ALIVE, null, false, UUID.randomUUID().toString()),
            new PlayerSnapshot(2, "Player2", LifeState.DEAD, null, false, UUID.randomUUID().toString()),
            new PlayerSnapshot(3, "Player3", LifeState.ALIVE, null, false, UUID.randomUUID().toString())
        );

        assertEquals(2, GameHudOverlay.formatPlayerCountText(players));
    }

    @Test
    void getPhaseColor_shouldReturnCorrectColors() {
        assertEquals(0xFFFFAA00, GameHudOverlay.getPhaseColor(GamePhase.DAY));
        assertEquals(0xFF5555FF, GameHudOverlay.getPhaseColor(GamePhase.NIGHT));
        assertEquals(0xFFFFFFFF, GameHudOverlay.getPhaseColor(GamePhase.SETUP));
        assertEquals(0xFFFFFFFF, GameHudOverlay.getPhaseColor(GamePhase.VOTING));
    }
}
