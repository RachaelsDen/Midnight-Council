package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import java.util.Objects;

public class PlayerEntry {
	private final int seatNumber;
	private final String displayName;
	private final boolean storyteller;
	private final PlayerReference playerReference;
	private LifeState lifeState;
	private SleepState sleepState;

	public PlayerEntry(int seatNumber, String displayName, boolean storyteller, PlayerReference playerReference) {
		this(seatNumber, displayName, LifeState.ALIVE, SleepState.AWAKE, storyteller, playerReference);
	}

	public PlayerEntry(
			int seatNumber,
			String displayName,
			LifeState lifeState,
			SleepState sleepState,
			boolean storyteller,
			PlayerReference playerReference) {
		if (seatNumber < 0) {
			throw new IllegalArgumentException("seatNumber cannot be negative");
		}

		String validatedDisplayName = Objects.requireNonNull(displayName, "displayName");
		if (validatedDisplayName.isBlank()) {
			throw new IllegalArgumentException("displayName cannot be blank");
		}

		this.seatNumber = seatNumber;
		this.displayName = validatedDisplayName;
		this.lifeState = Objects.requireNonNull(lifeState, "lifeState");
		this.sleepState = Objects.requireNonNull(sleepState, "sleepState");
		this.storyteller = storyteller;
		this.playerReference = Objects.requireNonNull(playerReference, "playerReference");
	}

	public int getSeatNumber() {
		return this.seatNumber;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public LifeState getLifeState() {
		return this.lifeState;
	}

	public void setLifeState(LifeState lifeState) {
		this.lifeState = Objects.requireNonNull(lifeState, "lifeState");
	}

	public SleepState getSleepState() {
		return this.sleepState;
	}

	public void setSleepState(SleepState sleepState) {
		this.sleepState = Objects.requireNonNull(sleepState, "sleepState");
	}

	public boolean isStoryteller() {
		return this.storyteller;
	}

	public PlayerReference getPlayerReference() {
		return this.playerReference;
	}

	public boolean isAlive() {
		return this.lifeState == LifeState.ALIVE;
	}

	public boolean isSleeping() {
		return this.sleepState == SleepState.SLEEPING;
	}

	public void kill() {
		this.setLifeState(LifeState.DEAD);
	}

	public void revive() {
		this.setLifeState(LifeState.ALIVE);
	}

	public void sleep() {
		this.setSleepState(SleepState.SLEEPING);
	}

	public void wake() {
		this.setSleepState(SleepState.AWAKE);
	}
}
