package dev.kgoodwin.midnightcouncil.fabric.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import org.junit.jupiter.api.Test;

class MidnightCommandTreeTest {

    @Test
    void statusShowsNoGameInProgressWhenIdle() {
        GameState gameState = new GameState();

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("No game in progress", status);
    }

    @Test
    void statusRendersPlayersAndCountsInSetup() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.getPlayers().register(new PlayerEntry(1, "Ana", false, PlayerReference.ofName("ana")));
        gameState.getPlayers().register(new PlayerEntry(2, "Ben", false, PlayerReference.ofName("ben")));
        gameState.getPlayers().register(new PlayerEntry(3, "Cal", false, PlayerReference.ofName("cal")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: SETUP | Players: 3 alive / 3 total | Day: 0 | Night: 0", status);
    }

    @Test
    void statusRendersAliveDeadPlayersInDayPhase() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setDayCount(2);

        gameState.getPlayers().register(new PlayerEntry(1, "Dia", false, PlayerReference.ofName("dia")));
        PlayerEntry deadPlayer = new PlayerEntry(2, "Eli", false, PlayerReference.ofName("eli"));
        deadPlayer.kill();
        gameState.getPlayers().register(deadPlayer);
        gameState.getPlayers().register(new PlayerEntry(3, "Fay", false, PlayerReference.ofName("fay")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: DAY | Players: 2 alive / 3 total | Day: 2 | Night: 0", status);
    }

    @Test
    void statusExcludesStorytellerFromPlayerCounts() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setDayCount(1);

        gameState.getPlayers().register(new PlayerEntry(1, "Ivy", false, PlayerReference.ofName("ivy")));
        gameState.getPlayers().register(new PlayerEntry(2, "Jax", false, PlayerReference.ofName("jax")));
        gameState.getPlayers().register(new PlayerEntry(15, "Narrator", true, PlayerReference.ofName("narrator")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: DAY | Players: 2 alive / 2 total | Day: 1 | Night: 0", status);
    }

    @Test
    void statusRendersNightCountInNightPhase() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setPhase(GamePhase.NIGHT);
        gameState.setNightCount(5);

        gameState.getPlayers().register(new PlayerEntry(1, "Gio", false, PlayerReference.ofName("gio")));
        gameState.getPlayers().register(new PlayerEntry(2, "Hal", false, PlayerReference.ofName("hal")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: NIGHT | Players: 2 alive / 2 total | Day: 0 | Night: 5", status);
    }
}
