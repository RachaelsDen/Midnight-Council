package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;

import java.util.Objects;

public class GameSession {

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
		dispatcher.dispatch(new PhaseChanged(oldPhase, target));
	}

	public void startSetup() {
		transitionPhase(GamePhase.SETUP);
	}

	public void startSeating() {
		transitionPhase(GamePhase.SEATING);
	}

	public void startGame() {
		transitionPhase(GamePhase.DAY);
		state.incrementDayCount();
	}

	public void endGame() {
		transitionPhase(GamePhase.GAME_OVER);
	}

	public void resetSession() {
		transitionPhase(GamePhase.IDLE);
		state.getPlayers().clear();
	}

	public PlayerEntry addPlayer(PlayerReference playerRef, String displayName, int seatNumber) {
		if (state.getPhase() != GamePhase.SETUP) {
			throw new IllegalStateException("Players can only be added during SETUP phase");
		}
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
}
