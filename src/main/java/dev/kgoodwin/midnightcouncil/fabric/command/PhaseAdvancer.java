package dev.kgoodwin.midnightcouncil.fabric.command;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

/**
 * Determines the next phase in the Midnight Council game flow.
 * Used by {@code /midnight phase [next]} to advance the game state.
 */
final class PhaseAdvancer {

    private PhaseAdvancer() {
    }

    static GamePhase nextPhase(GamePhase current) {
        return switch (current) {
            case IDLE -> GamePhase.SETUP;
            case SETUP -> GamePhase.SEATING;
            case SEATING -> GamePhase.DAY;
            case DAY -> GamePhase.NOMINATION;
            case NOMINATION -> GamePhase.VOTING;
            case VOTING -> GamePhase.EXECUTION;
            case EXECUTION -> GamePhase.DAY;
            case NIGHT -> GamePhase.DAY;
            case GAME_OVER -> null;
        };
    }
}
