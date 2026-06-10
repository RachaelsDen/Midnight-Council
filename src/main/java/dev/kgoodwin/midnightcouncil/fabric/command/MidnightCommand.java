package dev.kgoodwin.midnightcouncil.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;
import dev.kgoodwin.midnightcouncil.fabric.MidnightCouncilMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Registers the {@code /midnight} command tree with all game subcommands.
 * Storyteller commands require OP level 2; {@code /midnight vote} is available to all players.
 * Command handlers delegate to {@link GameSession} and managers — no game logic lives here.
 */
public class MidnightCommand {

    private static final Permission HAS_OP = new Permission.HasCommandLevel(PermissionLevel.byId(2));

    private MidnightCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(MidnightCommand::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            Commands.CommandSelection selection) {

        dispatcher.register(Commands.literal("midnight")
            .executes(MidnightCommand::help)

            .then(Commands.literal("setup")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .executes(MidnightCommand::setup))

            .then(Commands.literal("start")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .executes(MidnightCommand::start))

            .then(Commands.literal("phase")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .executes(MidnightCommand::phaseNext)
                .then(Commands.literal("next")
                    .executes(MidnightCommand::phaseNext)))

            .then(Commands.literal("nominate")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .then(Commands.argument("nominator", StringArgumentType.word())
                    .then(Commands.argument("nominee", StringArgumentType.word())
                        .executes(MidnightCommand::nominate))))

            .then(Commands.literal("vote")
                .then(Commands.argument("choice", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        String remaining = builder.getRemaining().toLowerCase();
                        for (String opt : new String[]{"yes", "no"}) {
                            if (opt.startsWith(remaining)) {
                                builder.suggest(opt);
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(MidnightCommand::vote)))

            .then(Commands.literal("execute")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(MidnightCommand::execute)))

            .then(Commands.literal("spare")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .executes(MidnightCommand::spare))

            .then(Commands.literal("end")
                .requires(src -> src.permissions().hasPermission(HAS_OP))
                .executes(MidnightCommand::end))
        );
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "\u00A76\u00A7lMidnight Council\u00A7r\n"
            + "\u00A7e/midnight setup\u00A7r - Start game setup\n"
            + "\u00A7e/midnight start\u00A7r - Start the game\n"
            + "\u00A7e/midnight phase [next]\u00A7r - Advance phase\n"
            + "\u00A7e/midnight nominate <nominator> <nominee>\u00A7r - Open nomination\n"
            + "\u00A7e/midnight vote <yes|no>\u00A7r - Cast vote\n"
            + "\u00A7e/midnight execute <player>\u00A7r - Execute player\n"
            + "\u00A7e/midnight spare\u00A7r - Spare player\n"
            + "\u00A7e/midnight end\u00A7r - End game"
        ), false);
        return 1;
    }

    private static int setup(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        try {
            session.startSetup();
            feedback(ctx, "Game setup started.");
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        try {
            session.startGame();
            feedback(ctx, "Game started! Entering DAY phase.");
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int phaseNext(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        GamePhase current = session.getState().getPhase();
        GamePhase next = PhaseAdvancer.nextPhase(current);
        if (next == null) {
            ctx.getSource().sendFailure(Component.literal(
                "No valid next phase from " + current));
            return 0;
        }
        try {
            session.transitionPhase(next);
            feedback(ctx, "Phase advanced to " + next);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int nominate(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        String nominatorName = StringArgumentType.getString(ctx, "nominator");
        String nomineeName = StringArgumentType.getString(ctx, "nominee");
        PlayerReference nominator = PlayerReference.ofName(nominatorName);
        PlayerReference nominee = PlayerReference.ofName(nomineeName);

        NominationManager nomManager = MidnightCouncilMod.getNominationManager();
        try {
            nomManager.nominate(session.getState(), nominator, nominee);
            feedback(ctx, nominatorName + " nominated " + nomineeName);
            return 1;
        } catch (IllegalStateException | IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int vote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameSession session = getSession();
        String choice = StringArgumentType.getString(ctx, "choice");
        if (!isValidVoteChoice(choice)) {
            ctx.getSource().sendFailure(Component.literal(
                "Invalid vote choice: " + choice + ". Use yes or no."));
            return 0;
        }

        String voterName = ctx.getSource().getPlayerOrException().getScoreboardName();
        PlayerReference voter = PlayerReference.ofName(voterName);
        boolean isYes = choice.equalsIgnoreCase("yes");

        VoteManager voteManager = MidnightCouncilMod.getVoteManager();
        try {
            voteManager.castVote(voter, isYes);
            feedback(ctx, "Vote recorded: " + choice);
            return 1;
        } catch (IllegalStateException | IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        String playerName = StringArgumentType.getString(ctx, "player");
        PlayerReference target = PlayerReference.ofName(playerName);

        ExecutionManager execManager = MidnightCouncilMod.getExecutionManager();
        try {
            execManager.execute(session.getState(), target);
            feedback(ctx, "Executed " + playerName);
            return 1;
        } catch (IllegalStateException | IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int spare(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        GamePhase current = session.getState().getPhase();

        if (current != GamePhase.EXECUTION) {
            ctx.getSource().sendFailure(Component.literal(
                "Can only spare during EXECUTION phase. Current: " + current));
            return 0;
        }

        try {
            session.transitionPhase(GamePhase.DAY);
            feedback(ctx, "Player spared. Returning to DAY phase.");
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int end(CommandContext<CommandSourceStack> ctx) {
        GameSession session = getSession();
        try {
            session.endGame();
            feedback(ctx, "Game ended.");
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static GameSession getSession() {
        return MidnightCouncilMod.getGameSession();
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    static boolean isValidVoteChoice(String choice) {
        return "yes".equalsIgnoreCase(choice) || "no".equalsIgnoreCase(choice);
    }
}
