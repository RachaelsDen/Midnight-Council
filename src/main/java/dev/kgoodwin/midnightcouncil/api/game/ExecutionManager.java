package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;

import java.util.Objects;

public class ExecutionManager {

	private final GameEventDispatcher dispatcher;

	public ExecutionManager(GameEventDispatcher dispatcher) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public void execute(GameState state, PlayerReference player) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(player, "player");
		if (state.getPhase() != GamePhase.EXECUTION) {
			throw new IllegalStateException("Players can only be executed during EXECUTION phase");
		}

		if (!canExecute(state, player)) {
			throw new IllegalStateException("Player cannot be executed: " + player.value());
		}

		PlayerEntry entry = state.getPlayers().getByPlayerReference(player).orElseThrow();
		entry.kill();
		state.setMarkedSeat(entry.getSeatNumber());
		dispatcher.dispatch(new ExecutionResolved(player, "lynch"));
	}

	public boolean canExecute(GameState state, PlayerReference player) {
		Objects.requireNonNull(state, "state");
		if (player == null) {
			return false;
		}
		if (state.getPhase() != GamePhase.EXECUTION) {
			return false;
		}

		return state.getPlayers().getByPlayerReference(player)
				.filter(PlayerEntry::isAlive)
				.isPresent();
	}
}
