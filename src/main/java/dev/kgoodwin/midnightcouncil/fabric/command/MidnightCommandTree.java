package dev.kgoodwin.midnightcouncil.fabric.command;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.TimerManager;
import dev.kgoodwin.midnightcouncil.fabric.MidnightCouncilMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Objects;

public final class MidnightCommandTree {

    private static final int MIN_SEAT = 1;
    private static final int MAX_SEAT = 15;

    private MidnightCommandTree() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MidnightCouncilMod mod, GameSession gameSession) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("midnight");

        root.then(Commands.literal("status")
                .executes(context -> {
                    String status = formatStatus(gameSession.getState());
                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                    return 1;
                }));

        root.then(Commands.literal("join")
                .executes(context -> executePlayerAction(context, () -> {
                    ServerPlayer player = getExecutingPlayer(context);
                    PlayerReference playerReference = PlayerReference.from(player.getUUID());
                    int seatNumber = findNextAvailableSeat(gameSession.getState());
                    gameSession.addPlayer(playerReference, player.getName().getString(), seatNumber);
                    return "Joined as seat " + seatNumber;
                })));

        root.then(Commands.literal("leave")
                .executes(context -> executePlayerAction(context, () -> {
                    ServerPlayer player = getExecutingPlayer(context);
                    gameSession.removePlayer(PlayerReference.from(player.getUUID()));
                    return "Left the game";
                })));

        root.then(Commands.literal("setup")
                .executes(context -> executeStorytellerAction(context, () -> {
                    gameSession.startSetup();
                    return "Setup phase started";
                })));

        root.then(Commands.literal("start")
                .executes(context -> executeStorytellerAction(context, () -> {
                    gameSession.startSeating();
                    return "Seating phase started";
                })));

        root.then(Commands.literal("phase")
                .then(Commands.argument("phase", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            GamePhase current = gameSession.getState().getPhase();
                            for (GamePhase phase : GamePhase.values()) {
                                if (current.canTransitionTo(phase)) {
                                    builder.suggest(phase.name().toLowerCase(Locale.ROOT));
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeStorytellerAction(context, () -> {
                            GamePhase targetPhase = parsePhaseName(StringArgumentType.getString(context, "phase"));
                            changePhase(gameSession, targetPhase);
                            return "Phase changed to " + targetPhase;
                        }))));

        LiteralArgumentBuilder<CommandSourceStack> nominate = Commands.literal("nominate");
        nominate.then(Commands.argument("nominator", EntityArgument.player())
                .then(Commands.argument("nominee", EntityArgument.player())
                        .executes(context -> executeStorytellerAction(context, () -> {
                            ServerPlayer nominator = EntityArgument.getPlayer(context, "nominator");
                            ServerPlayer nominee = EntityArgument.getPlayer(context, "nominee");
                            mod.nominationManager().nominate(
                                    gameSession.getState(),
                                    PlayerReference.from(nominator.getUUID()),
                                    PlayerReference.from(nominee.getUUID()));
                            return "Nomination opened: " + nominator.getName().getString()
                                    + " nominated " + nominee.getName().getString();
                        }))));
        root.then(nominate);

        root.then(Commands.literal("vote")
                .then(Commands.literal("yes")
                        .executes(context -> executePlayerAction(context, () -> {
                            ServerPlayer player = getExecutingPlayer(context);
                            mod.voteManager().castVote(PlayerReference.from(player.getUUID()), true);
                            return "Voted YES";
                        })))
                .then(Commands.literal("no")
                        .executes(context -> executePlayerAction(context, () -> {
                            ServerPlayer player = getExecutingPlayer(context);
                            mod.voteManager().castVote(PlayerReference.from(player.getUUID()), false);
                            return "Voted NO";
                        }))));

        root.then(Commands.literal("execute")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> executeStorytellerAction(context, () -> {
                            ServerPlayer storyteller = getExecutingPlayer(context);
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            mod.executionManager().execute(
                                    gameSession.getState(),
                                    PlayerReference.from(storyteller.getUUID()),
                                    PlayerReference.from(target.getUUID()));
                            return "Player executed";
                        }))));

        root.then(Commands.literal("timer")
                .then(Commands.literal("discussion")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            requireTimerManager(mod).startDiscussionTimer();
                            return "Discussion timer started";
                        })))
                .then(Commands.literal("nomination")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            requireTimerManager(mod).startNominationTimer();
                            return "Nomination timer started";
                        })))
                .then(Commands.literal("stop")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            requireTimerManager(mod).stopTimer();
                            return "Timer stopped";
                        }))));

        dispatcher.register(root);
    }

    static String formatStatus(GameState state) {
        if (state.getPhase() == GamePhase.IDLE) {
            return "No game in progress";
        }

        long nonStorytellerTotal = state.getPlayers().getPlayers().stream()
                .filter(entry -> !entry.isStoryteller())
                .count();

        return "Phase: " + state.getPhase()
                + " | Players: " + state.getAliveCount() + " alive / " + nonStorytellerTotal + " total"
                + " | Day: " + state.getDayCount()
                + " | Night: " + state.getNightCount();
    }

    static GamePhase parsePhaseName(String phaseName) {
        Objects.requireNonNull(phaseName, "phaseName");
        try {
            return GamePhase.valueOf(phaseName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown phase: " + phaseName);
        }
    }

    static int findNextAvailableSeat(GameState state) {
        for (int seat = MIN_SEAT; seat <= MAX_SEAT; seat++) {
            int currentSeat = seat;
            boolean occupied = state.getPlayers().getPlayers().stream()
                    .map(PlayerEntry::getSeatNumber)
                    .anyMatch(seatNumber -> seatNumber == currentSeat);
            if (!occupied) {
                return seat;
            }
        }
        throw new IllegalStateException("No available seats");
    }

    static int executePlayerAction(CommandContext<CommandSourceStack> context, ThrowingSupplier<String> action) {
        return executeCommand(context.getSource(), action);
    }

    static int executeStorytellerAction(CommandContext<CommandSourceStack> context, ThrowingSupplier<String> action) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = getExecutingPlayer(context);
            if (!source.getServer().getPlayerList().isOp(player.nameAndId())) {
                source.sendFailure(Component.literal("Only storytellers can use this command"));
                return 0;
            }
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal(userFacingMessage(exception)));
            return 0;
        }
        return executeCommand(source, action);
    }

    static int executeCommand(CommandSourceStack source, ThrowingSupplier<String> action) {
        try {
            String message = action.get();
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | CommandSyntaxException exception) {
            source.sendFailure(Component.literal(userFacingMessage(exception)));
            return 0;
        }
    }

    private static ServerPlayer getExecutingPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getExecutingPlayer(context.getSource());
    }

    private static ServerPlayer getExecutingPlayer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
        return player;
    }

    private static void changePhase(GameSession gameSession, GamePhase targetPhase) {
        GamePhase currentPhase = gameSession.getState().getPhase();
        if (targetPhase == GamePhase.DAY && currentPhase == GamePhase.SEATING) {
            gameSession.startGame();
            return;
        }
        if (targetPhase == GamePhase.NIGHT) {
            gameSession.startNight();
            return;
        }
        if (targetPhase == GamePhase.GAME_OVER) {
            gameSession.endGame();
            return;
        }
        gameSession.transitionPhase(targetPhase);
    }

    private static TimerManager requireTimerManager(MidnightCouncilMod mod) {
        TimerManager timerManager = mod.timerManager();
        if (timerManager == null) {
            throw new IllegalStateException("Timer manager is unavailable");
        }
        return timerManager;
    }

    private static String userFacingMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "This command can only be run by a player";
        }
        return message;
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws CommandSyntaxException;
    }
}
