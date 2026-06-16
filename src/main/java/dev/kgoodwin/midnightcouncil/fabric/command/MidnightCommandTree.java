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

        root.then(Commands.literal("storyteller")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> executeStorytellerAction(context, () -> {
							ServerPlayer player = EntityArgument.getPlayer(context, "player");
							if (!context.getSource().getServer().getPlayerList().isOp(player.nameAndId())) {
								throw new IllegalStateException("Target player must have operator permissions to be a storyteller");
							}
							PlayerReference playerReference = PlayerReference.from(player.getUUID());
							gameSession.addStoryteller(playerReference, player.getName().getString());
							return player.getName().getString() + " registered as storyteller";
						}))));

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
                            changePhase(mod, gameSession, targetPhase);
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
                .then(Commands.literal("start")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            GameState state = gameSession.getState();
                            int nominatedSeat = state.getNominatedSeat().orElseThrow(
                                    () -> new IllegalStateException("No nominee to vote on"));
                            PlayerEntry nominee = state.getPlayers().getBySeatNumber(nominatedSeat).orElseThrow(
                                    () -> new IllegalStateException("Nominated seat has no player"));
                            mod.voteManager().startVote(state, nominee.getPlayerReference());
                            return "Vote started for " + nominee.getDisplayName();
                        }))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> executeStorytellerAction(context, () -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    mod.voteManager().startVote(
                                            gameSession.getState(), PlayerReference.from(player.getUUID()));
                                    return "Vote started for " + player.getName().getString();
                                }))))
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
                            requireTimerManager(mod).startDiscussionTimer(gameSession.getState());
                            return "Discussion timer started";
                        })))
                .then(Commands.literal("nomination")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            requireTimerManager(mod).startNominationTimer(gameSession.getState());
                            return "Nomination timer started";
                        })))
                .then(Commands.literal("stop")
                        .executes(context -> executeStorytellerAction(context, () -> {
                            requireTimerManager(mod).stopTimer(gameSession.getState());
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

	private static void changePhase(MidnightCouncilMod mod, GameSession gameSession, GamePhase targetPhase) {
		GamePhase currentPhase = gameSession.getState().getPhase();
		GameState state = gameSession.getState();

		if (currentPhase == GamePhase.VOTING && targetPhase != GamePhase.VOTING) {
			mod.voteManager().reset();
		}

		if (targetPhase == GamePhase.DAY && currentPhase == GamePhase.SEATING) {
			gameSession.startGame();
			mod.nominationManager().resetForNewDay(state);
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
		if (targetPhase == GamePhase.IDLE) {
			TimerManager tm = mod.timerManager();
			if (tm != null && tm.isTimerRunning()) {
				tm.stopTimer(state);
			}
			gameSession.resetSession();
			mod.voteManager().reset();
			mod.nominationManager().resetForNewDay(state);
			return;
		}
		if (targetPhase == GamePhase.VOTING) {
			gameSession.transitionPhase(GamePhase.VOTING);
			state.getNominatedSeat().ifPresent(seat -> {
				state.getPlayers().getBySeatNumber(seat).ifPresent(nominee -> {
					mod.voteManager().startVote(state, nominee.getPlayerReference());
				});
			});
			return;
		}
		if (targetPhase == GamePhase.DAY) {
			gameSession.transitionPhase(GamePhase.DAY);
			if (currentPhase == GamePhase.NIGHT || currentPhase == GamePhase.EXECUTION) {
				mod.nominationManager().resetForNewDay(state);
			}
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
