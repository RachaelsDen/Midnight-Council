package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

import java.util.Objects;
import java.util.OptionalInt;

public class GameState {
	private GamePhase phase;
	private final PlayerRegistry players;
	private Integer nominatedSeat;
	private Integer markedSeat;
	private boolean timerActive;
	private int dayCount;
	private int nightCount;

	public GameState() {
		this.phase = GamePhase.IDLE;
		this.players = new PlayerRegistry();
	}

	static GameState reconstruct(GamePhase phase, int dayCount, int nightCount,
			Integer nominatedSeat, Integer markedSeat, boolean timerActive) {
		GameState state = new GameState();
		state.phase = Objects.requireNonNull(phase, "phase");
		state.dayCount = dayCount;
		state.nightCount = nightCount;
		state.nominatedSeat = nominatedSeat;
		state.markedSeat = markedSeat;
		state.timerActive = timerActive;
		return state;
	}

	public GamePhase getPhase() {
		return this.phase;
	}

	public void setPhase(GamePhase phase) {
		this.phase = this.phase.transitionTo(Objects.requireNonNull(phase, "phase"));
	}

	public PlayerRegistry getPlayers() {
		return this.players;
	}

	public OptionalInt getNominatedSeat() {
		return this.nominatedSeat == null ? OptionalInt.empty() : OptionalInt.of(this.nominatedSeat);
	}

	public void setNominatedSeat(int nominatedSeat) {
		validateSeatNumber(nominatedSeat, "nominatedSeat");
		this.nominatedSeat = nominatedSeat;
	}

	public void clearNominatedSeat() {
		this.nominatedSeat = null;
	}

	public OptionalInt getMarkedSeat() {
		return this.markedSeat == null ? OptionalInt.empty() : OptionalInt.of(this.markedSeat);
	}

	public void setMarkedSeat(int markedSeat) {
		validateSeatNumber(markedSeat, "markedSeat");
		this.markedSeat = markedSeat;
	}

	public void clearMarkedSeat() {
		this.markedSeat = null;
	}

	public boolean isTimerActive() {
		return this.timerActive;
	}

	public void setTimerActive(boolean timerActive) {
		this.timerActive = timerActive;
	}

	public int getDayCount() {
		return this.dayCount;
	}

	public void setDayCount(int dayCount) {
		validateCount(dayCount, "dayCount");
		this.dayCount = dayCount;
	}

	public void incrementDayCount() {
		this.dayCount++;
	}

	public int getNightCount() {
		return this.nightCount;
	}

	public void setNightCount(int nightCount) {
		validateCount(nightCount, "nightCount");
		this.nightCount = nightCount;
	}

	public void incrementNightCount() {
		this.nightCount++;
	}

	public int getAliveCount() {
		return this.players.getAliveCount();
	}

	private static void validateSeatNumber(int seatNumber, String fieldName) {
		if (seatNumber < 0) {
			throw new IllegalArgumentException(fieldName + " cannot be negative");
		}
	}

	private static void validateCount(int count, String fieldName) {
		if (count < 0) {
			throw new IllegalArgumentException(fieldName + " cannot be negative");
		}
	}
}
