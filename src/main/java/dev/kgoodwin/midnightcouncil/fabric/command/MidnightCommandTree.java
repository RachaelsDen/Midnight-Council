package dev.kgoodwin.midnightcouncil.fabric.command;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class MidnightCommandTree {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameSession gameSession) {
        dispatcher.register(
                Commands.literal("midnight")
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    String status = formatStatus(gameSession.getState());
                                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                                    return 1;
                                }))
        );
    }

    static String formatStatus(GameState state) {
        if (state.getPhase() == GamePhase.IDLE) {
            return "No game in progress";
        }

        return "Phase: " + state.getPhase()
                + " | Players: " + state.getAliveCount() + " alive / " + state.getPlayers().getPlayers().size() + " total"
                + " | Day: " + state.getDayCount()
                + " | Night: " + state.getNightCount();
    }
}
