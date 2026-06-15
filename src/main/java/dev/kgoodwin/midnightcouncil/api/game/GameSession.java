package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;

public class GameSession {

	private static final int MIN_SEAT_NUMBER = 1;
	private static final int MIN_PLAYERS = 5;
	private static final int MAX_PLAYERS = 15;

	private final GameState state;
	private final GameEventDispatcher dispatcher;

	public GameSession() {
		this.state = new GameState();
		this.dispatcher = new GameEventDispatcher();
	}

	public GameState getState() {
		return state;
	}

	public GameEventDispatcher getDispatcher() {
		return dispatcher;
	}

	public void transitionPhase(GamePhase target) {
		GamePhase oldPhase = state.getPhase();
		state.setPhase(target);
		if (target == GamePhase.DAY) {
			state.incrementDayCount();
		} else if (target == GamePhase.NIGHT) {
			state.incrementNightCount();
		}
		if (target != GamePhase.VOTING && target != GamePhase.EXECUTION) {
			state.clearNominatedSeat();
		}
		dispatcher.dispatch(new PhaseChanged(oldPhase, target));
	}

	public void startSetup() {
		transitionPhase(GamePhase.SETUP);
	}

	public void startSeating() {
		transitionPhase(GamePhase.SEATING);
	}

	public void startGame() {
		validateSupportedPlayerCount();
		transitionPhase(GamePhase.DAY);
	}

	public void startNight() {
		validateSupportedPlayerCount();
		transitionPhase(GamePhase.NIGHT);
	}

	public void endGame() {
		transitionPhase(GamePhase.GAME_OVER);
	}

	public void resetSession() {
		GamePhase oldPhase = state.getPhase();
		state.resetSessionState();
		if (oldPhase != GamePhase.IDLE) {
			dispatcher.dispatch(new PhaseChanged(oldPhase, GamePhase.IDLE));
		}
	}

	public PlayerEntry addPlayer(PlayerReference playerRef, String displayName, int seatNumber) {
		if (state.getPhase() != GamePhase.SETUP) {
			throw new IllegalStateException("Players can only be added during SETUP phase");
		}
		validateSeatNumber(seatNumber);
		PlayerEntry entry = new PlayerEntry(seatNumber, displayName, false, playerRef);
		state.getPlayers().register(entry);
		dispatcher.dispatch(new PlayerStateChanged(playerRef, "joined"));
		return entry;
	}

	public void removePlayer(PlayerReference playerRef) {
		if (state.getPhase() != GamePhase.SETUP) {
			throw new IllegalStateException("Players can only be removed during SETUP phase");
		}
		state.getPlayers().unclaim(playerRef)
				.orElseThrow(() -> new IllegalArgumentException("Player not registered: " + playerRef.value()));
		dispatcher.dispatch(new PlayerStateChanged(playerRef, "left"));
	}

	public void setPlayerAlive(PlayerReference playerRef, boolean alive) {
		PlayerEntry entry = getRequiredPlayer(playerRef);
		if (alive) {
			entry.revive();
		} else {
			entry.kill();
		}
		dispatcher.dispatch(new PlayerStateChanged(playerRef, alive ? "revived" : "killed"));
	}

	public void setPlayerAsleep(PlayerReference playerRef, boolean asleep) {
		PlayerEntry entry = getRequiredPlayer(playerRef);
		if (asleep) {
			entry.sleep();
		} else {
			entry.wake();
		}
		dispatcher.dispatch(new PlayerStateChanged(playerRef, asleep ? "asleep" : "awake"));
	}

	private PlayerEntry getRequiredPlayer(PlayerReference playerRef) {
		return state.getPlayers().getByPlayerReference(playerRef)
				.orElseThrow(() -> new IllegalArgumentException("Player not registered: " + playerRef.value()));
	}

	private int countNonStorytellerPlayers() {
		return (int) state.getPlayers().getPlayers().stream()
				.filter(entry -> !entry.isStoryteller())
				.count();
	}

	private void validateSupportedPlayerCount() {
		int playerCount = countNonStorytellerPlayers();
		if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
			throw new IllegalStateException(
					"Games require between " + MIN_PLAYERS + " and " + MAX_PLAYERS + " non-storyteller players");
		}
	}

	private void validateSeatNumber(int seatNumber) {
		if (seatNumber < MIN_SEAT_NUMBER || seatNumber > MAX_PLAYERS) {
			throw new IllegalArgumentException(
					"Seat number must be between " + MIN_SEAT_NUMBER + " and " + MAX_PLAYERS);
		}
	}
}
