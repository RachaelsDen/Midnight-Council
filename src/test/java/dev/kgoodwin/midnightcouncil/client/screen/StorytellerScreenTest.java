package dev.kgoodwin.midnightcouncil.client.screen;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorytellerScreenTest {

    @Test
    void testPhasePanelSelection() {
        // Null phase defaults to IDLE
        assertEquals(PhasePanelSelector.PanelType.IDLE, PhasePanelSelector.selectPanel(null));
        
        // Every phase maps to its corresponding panel
        assertEquals(PhasePanelSelector.PanelType.IDLE, PhasePanelSelector.selectPanel(GamePhase.IDLE));
        assertEquals(PhasePanelSelector.PanelType.SETUP, PhasePanelSelector.selectPanel(GamePhase.SETUP));
        assertEquals(PhasePanelSelector.PanelType.SEATING, PhasePanelSelector.selectPanel(GamePhase.SEATING));
        assertEquals(PhasePanelSelector.PanelType.DAY, PhasePanelSelector.selectPanel(GamePhase.DAY));
        assertEquals(PhasePanelSelector.PanelType.NOMINATION, PhasePanelSelector.selectPanel(GamePhase.NOMINATION));
        assertEquals(PhasePanelSelector.PanelType.VOTING, PhasePanelSelector.selectPanel(GamePhase.VOTING));
        assertEquals(PhasePanelSelector.PanelType.EXECUTION, PhasePanelSelector.selectPanel(GamePhase.EXECUTION));
        assertEquals(PhasePanelSelector.PanelType.NIGHT, PhasePanelSelector.selectPanel(GamePhase.NIGHT));
        assertEquals(PhasePanelSelector.PanelType.GAME_OVER, PhasePanelSelector.selectPanel(GamePhase.GAME_OVER));
    }

    @Test
    void testPlayerSnapshotParsing() {
        ClientGameState.PlayerSnapshot player = new ClientGameState.PlayerSnapshot(
                "TestPlayer", 1, LifeState.ALIVE, SleepState.AWAKE);
        
        assertEquals(LifeState.ALIVE, player.lifeState());
        assertEquals(SleepState.AWAKE, player.sleepState());
    }
}
