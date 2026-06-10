package dev.kgoodwin.midnightcouncil.fabric.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MidnightCommandTest {

    @Nested
    @DisplayName("isValidVoteChoice")
    class IsValidVoteChoice {

        @ParameterizedTest
        @ValueSource(strings = {"yes", "Yes", "YES", "no", "No", "NO"})
        void validChoices(String input) {
            assertTrue(MidnightCommand.isValidVoteChoice(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"maybe", "true", "false", "aye", " "})
        void invalidChoices(String input) {
            assertFalse(MidnightCommand.isValidVoteChoice(input));
        }

        @Test
        void blankStringIsInvalid() {
            assertFalse(MidnightCommand.isValidVoteChoice(""));
        }
    }

    @Nested
    @DisplayName("PhaseAdvancer")
    class PhaseAdvancerTests {

        @Test
        void idleAdvancesToSetup() {
            assertEquals(GamePhase.SETUP, PhaseAdvancer.nextPhase(GamePhase.IDLE));
        }

        @Test
        void setupAdvancesToSeating() {
            assertEquals(GamePhase.SEATING, PhaseAdvancer.nextPhase(GamePhase.SETUP));
        }

        @Test
        void seatingAdvancesToDay() {
            assertEquals(GamePhase.DAY, PhaseAdvancer.nextPhase(GamePhase.SEATING));
        }

        @Test
        void dayAdvancesToNomination() {
            assertEquals(GamePhase.NOMINATION, PhaseAdvancer.nextPhase(GamePhase.DAY));
        }

        @Test
        void nominationAdvancesToVoting() {
            assertEquals(GamePhase.VOTING, PhaseAdvancer.nextPhase(GamePhase.NOMINATION));
        }

        @Test
        void votingAdvancesToExecution() {
            assertEquals(GamePhase.EXECUTION, PhaseAdvancer.nextPhase(GamePhase.VOTING));
        }

        @Test
        void executionAdvancesToDay() {
            assertEquals(GamePhase.DAY, PhaseAdvancer.nextPhase(GamePhase.EXECUTION));
        }

        @Test
        void nightAdvancesToDay() {
            assertEquals(GamePhase.DAY, PhaseAdvancer.nextPhase(GamePhase.NIGHT));
        }

        @Test
        void gameOverReturnsNull() {
            assertNull(PhaseAdvancer.nextPhase(GamePhase.GAME_OVER));
        }
    }

    @Nested
    @DisplayName("Setup command delegation")
    class SetupDelegation {

        @Test
        void startSetupTransitionsFromIdleToSetup() {
            GameSession session = new GameSession();
            assertEquals(GamePhase.IDLE, session.getState().getPhase());

            session.startSetup();
            assertEquals(GamePhase.SETUP, session.getState().getPhase());
        }

        @Test
        void startSetupThrowsWhenNotIdle() {
            GameSession session = new GameSession();
            session.startSetup();
            assertThrows(IllegalStateException.class, session::startSetup);
        }
    }

    @Nested
    @DisplayName("Start command delegation")
    class StartDelegation {

        @Test
        void startGameThrowsWithoutPlayers() {
            GameSession session = new GameSession();
            session.startSetup();
            session.startSeating();
            assertThrows(IllegalStateException.class, session::startGame);
        }

        @Test
        void startGameTransitionsToDayWithEnoughPlayers() {
            GameSession session = new GameSession();
            session.startSetup();
            registerPlayers(session, 5);
            session.startSeating();
            session.startGame();
            assertEquals(GamePhase.DAY, session.getState().getPhase());
        }
    }

    @Nested
    @DisplayName("Nominate command delegation")
    class NominateDelegation {

        @Test
        void nominateSucceedsDuringNominationPhase() {
            GameSession session = new GameSession();
            NominationManager nomManager = new NominationManager(session.getDispatcher());
            setupSessionForNomination(session, 5);

            PlayerReference nominator = PlayerReference.ofName("p1");
            PlayerReference nominee = PlayerReference.ofName("p2");

            assertTrue(nomManager.canNominate(session.getState(), nominator, nominee));
            nomManager.nominate(session.getState(), nominator, nominee);
        }

        @Test
        void nominateFailsOutsideNominationPhase() {
            GameSession session = new GameSession();
            NominationManager nomManager = new NominationManager(session.getDispatcher());

            PlayerReference nominator = PlayerReference.ofName("p1");
            PlayerReference nominee = PlayerReference.ofName("p2");

            assertFalse(nomManager.canNominate(session.getState(), nominator, nominee));
        }
    }

    @Nested
    @DisplayName("Vote command delegation")
    class VoteDelegation {

        @Test
        void castVoteRecordsYes() {
            GameSession session = new GameSession();
            VoteManager voteManager = new VoteManager(session.getDispatcher());
            setupSessionForVoting(session, 5, voteManager);

            PlayerReference voter = PlayerReference.ofName("p1");
            voteManager.castVote(voter, true);
            assertEquals(VoteManager.VoteState.VOTED_YES, voteManager.getVoteState(voter));
        }

        @Test
        void castVoteRecordsNo() {
            GameSession session = new GameSession();
            VoteManager voteManager = new VoteManager(session.getDispatcher());
            setupSessionForVoting(session, 5, voteManager);

            PlayerReference voter = PlayerReference.ofName("p1");
            voteManager.castVote(voter, false);
            assertEquals(VoteManager.VoteState.VOTED_NO, voteManager.getVoteState(voter));
        }

        @Test
        void castVoteRejectsUnknownPlayer() {
            GameSession session = new GameSession();
            VoteManager voteManager = new VoteManager(session.getDispatcher());
            setupSessionForVoting(session, 5, voteManager);

            PlayerReference unknown = PlayerReference.ofName("stranger");
            assertThrows(IllegalArgumentException.class,
                () -> voteManager.castVote(unknown, true));
        }
    }

    @Nested
    @DisplayName("Execute command delegation")
    class ExecuteDelegation {

        @Test
        void executeKillsPlayerDuringExecutionPhase() {
            GameSession session = new GameSession();
            ExecutionManager execManager = new ExecutionManager(session.getDispatcher());
            setupSessionForExecution(session, 5);

            PlayerReference target = PlayerReference.ofName("p1");
            assertTrue(execManager.canExecute(session.getState(), target));

            execManager.execute(session.getState(), target);
            assertFalse(session.getState().getPlayers()
                .getByPlayerReference(target).get().isAlive());
        }

        @Test
        void executeFailsOutsideExecutionPhase() {
            GameSession session = new GameSession();
            ExecutionManager execManager = new ExecutionManager(session.getDispatcher());
            setupSessionForDay(session, 5);

            PlayerReference target = PlayerReference.ofName("p1");
            assertFalse(execManager.canExecute(session.getState(), target));
        }

        @Test
        void executeFailsForDeadPlayer() {
            GameSession session = new GameSession();
            ExecutionManager execManager = new ExecutionManager(session.getDispatcher());
            setupSessionForExecution(session, 5);

            PlayerReference target = PlayerReference.ofName("p1");
            session.setPlayerAlive(target, false);

            assertFalse(execManager.canExecute(session.getState(), target));
        }
    }

    @Nested
    @DisplayName("Spare command delegation")
    class SpareDelegation {

        @Test
        void spareTransitionsExecutionToDay() {
            GameSession session = new GameSession();
            setupSessionForExecution(session, 5);

            session.transitionPhase(GamePhase.DAY);
            assertEquals(GamePhase.DAY, session.getState().getPhase());
        }
    }

    @Nested
    @DisplayName("End command delegation")
    class EndDelegation {

        @Test
        void endGameTransitionsToGameOver() {
            GameSession session = new GameSession();
            setupSessionForDay(session, 5);

            session.endGame();
            assertEquals(GamePhase.GAME_OVER, session.getState().getPhase());
        }
    }

    private static void registerPlayers(GameSession session, int count) {
        for (int i = 1; i <= count; i++) {
            session.addPlayer(PlayerReference.ofName("p" + i), "Player" + i, i);
        }
    }

    private static void setupSessionForDay(GameSession session, int playerCount) {
        session.startSetup();
        registerPlayers(session, playerCount);
        session.startSeating();
        session.startGame();
    }

    private static void setupSessionForNomination(GameSession session, int playerCount) {
        setupSessionForDay(session, playerCount);
        session.transitionPhase(GamePhase.NOMINATION);
    }

    private static void setupSessionForVoting(GameSession session, int playerCount,
            VoteManager voteManager) {
        setupSessionForNomination(session, playerCount);
        PlayerReference nominee = PlayerReference.ofName("p2");
        session.transitionPhase(GamePhase.VOTING);
        voteManager.startVote(session.getState(), nominee);
    }

    private static void setupSessionForExecution(GameSession session, int playerCount) {
        setupSessionForDay(session, playerCount);
        session.transitionPhase(GamePhase.NOMINATION);
        session.transitionPhase(GamePhase.VOTING);
        session.transitionPhase(GamePhase.EXECUTION);
    }
}
