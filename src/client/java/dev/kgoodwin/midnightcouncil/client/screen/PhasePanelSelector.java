package dev.kgoodwin.midnightcouncil.client.screen;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

import java.util.Optional;

public final class PhasePanelSelector {
    
    private PhasePanelSelector() {}

    public enum PanelType {
        IDLE, SETUP, SEATING, DAY, NOMINATION, VOTING, EXECUTION, NIGHT, GAME_OVER, UNKNOWN
    }

    public static PanelType selectPanel(GamePhase phase) {
        if (phase == null) {
            return PanelType.IDLE;
        }

        return switch (phase) {
            case IDLE -> PanelType.IDLE;
            case SETUP -> PanelType.SETUP;
            case SEATING -> PanelType.SEATING;
            case DAY -> PanelType.DAY;
            case NOMINATION -> PanelType.NOMINATION;
            case VOTING -> PanelType.VOTING;
            case EXECUTION -> PanelType.EXECUTION;
            case NIGHT -> PanelType.NIGHT;
            case GAME_OVER -> PanelType.GAME_OVER;
        };
    }
}
