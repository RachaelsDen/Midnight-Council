package dev.kgoodwin.midnightcouncil.client.state;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ClientGameState {

	private volatile GameStateSnapshot current;
	private volatile String localPlayerName;
	private volatile VoiceHandshakeState voiceHandshake;

	public Optional<GameStateSnapshot> current() {
		return Optional.ofNullable(this.current);
	}

	public void update(GameStateSnapshot snapshot) {
		this.current = Objects.requireNonNull(snapshot, "snapshot");
	}

	public void setLocalPlayer(String name) {
		String validatedName = Objects.requireNonNull(name, "name");
		if (validatedName.isBlank()) {
			throw new IllegalArgumentException("name cannot be blank");
		}

		this.localPlayerName = validatedName;
	}

	public Optional<String> localPlayerName() {
		return Optional.ofNullable(this.localPlayerName);
	}

	public void updateVoiceHandshake(VoiceHandshakeState handshake) {
		this.voiceHandshake = Objects.requireNonNull(handshake, "handshake");
	}

	public Optional<VoiceHandshakeState> voiceHandshake() {
		return Optional.ofNullable(this.voiceHandshake);
	}

	public void clear() {
		this.current = null;
		this.localPlayerName = null;
		this.voiceHandshake = null;
	}

	public record GameStateSnapshot(
			GamePhase phase,
			int dayCount,
			int nightCount,
			Integer nominatedSeat,
			Integer markedSeat,
			boolean timerActive,
			List<PlayerSnapshot> players) {

		public GameStateSnapshot {
			Objects.requireNonNull(phase, "phase");
			if (dayCount < 0) {
				throw new IllegalArgumentException("dayCount cannot be negative");
			}
			if (nightCount < 0) {
				throw new IllegalArgumentException("nightCount cannot be negative");
			}
			if (nominatedSeat != null && nominatedSeat < 0) {
				throw new IllegalArgumentException("nominatedSeat cannot be negative");
			}
			if (markedSeat != null && markedSeat < 0) {
				throw new IllegalArgumentException("markedSeat cannot be negative");
			}

			players = List.copyOf(Objects.requireNonNull(players, "players"));
		}

		public static GameStateSnapshot from(GameState state) {
			Objects.requireNonNull(state, "state");

			List<PlayerSnapshot> players = state.getPlayers().getPlayers().stream()
					.map(PlayerSnapshot::from)
					.toList();

			return new GameStateSnapshot(
					state.getPhase(),
					state.getDayCount(),
					state.getNightCount(),
					state.getNominatedSeat().isPresent() ? state.getNominatedSeat().getAsInt() : null,
					state.getMarkedSeat().isPresent() ? state.getMarkedSeat().getAsInt() : null,
					state.isTimerActive(),
					players);
		}
	}

	public record PlayerSnapshot(String name, int seatIndex, LifeState lifeState, SleepState sleepState) {

		public PlayerSnapshot {
			String validatedName = Objects.requireNonNull(name, "name");
			if (validatedName.isBlank()) {
				throw new IllegalArgumentException("name cannot be blank");
			}
			if (seatIndex < 0) {
				throw new IllegalArgumentException("seatIndex cannot be negative");
			}

			Objects.requireNonNull(lifeState, "lifeState");
			Objects.requireNonNull(sleepState, "sleepState");
		}

		public static PlayerSnapshot from(PlayerEntry entry) {
			Objects.requireNonNull(entry, "entry");
			return new PlayerSnapshot(
					entry.getDisplayName(),
					entry.getSeatNumber(),
					entry.getLifeState(),
					entry.getSleepState());
		}
	}

	public record VoiceHandshakeState(String host, int port, byte[] connectToken) {

		public VoiceHandshakeState {
			String validatedHost = Objects.requireNonNull(host, "host");
			if (validatedHost.isBlank()) {
				throw new IllegalArgumentException("host cannot be blank");
			}
			if (port <= 0 || port > 65535) {
				throw new IllegalArgumentException("port must be between 1 and 65535");
			}

			connectToken = Objects.requireNonNull(connectToken, "connectToken").clone();
		}

		@Override
		public byte[] connectToken() {
			return this.connectToken.clone();
		}
	}
}
