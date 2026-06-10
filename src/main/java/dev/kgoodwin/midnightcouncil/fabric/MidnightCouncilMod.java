package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;
import dev.kgoodwin.midnightcouncil.fabric.command.MidnightCommand;
import net.fabricmc.api.ModInitializer;

public final class MidnightCouncilMod implements ModInitializer {

    private static final GameSession gameSession = new GameSession();
    private static final VoteManager voteManager = new VoteManager(gameSession.getDispatcher());
    private static final NominationManager nominationManager = new NominationManager(gameSession.getDispatcher());
    private static final ExecutionManager executionManager = new ExecutionManager(gameSession.getDispatcher());

    @Override
    public void onInitialize() {
        MidnightCommand.register();
    }

    public static GameSession getGameSession() {
        return gameSession;
    }

    public static VoteManager getVoteManager() {
        return voteManager;
    }

    public static NominationManager getNominationManager() {
        return nominationManager;
    }

    public static ExecutionManager getExecutionManager() {
        return executionManager;
    }
}
