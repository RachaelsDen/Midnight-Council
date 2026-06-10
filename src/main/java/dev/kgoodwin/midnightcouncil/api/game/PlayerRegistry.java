package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PlayerRegistry {
	private final Map<PlayerReference, PlayerEntry> playersByReference = new LinkedHashMap<>();
	private final Map<Integer, PlayerEntry> playersBySeat = new LinkedHashMap<>();

	public PlayerEntry register(PlayerEntry playerEntry) {
		PlayerEntry validatedPlayerEntry = Objects.requireNonNull(playerEntry, "playerEntry");
		PlayerReference playerReference = validatedPlayerEntry.getPlayerReference();
		int seatNumber = validatedPlayerEntry.getSeatNumber();

		if (this.playersByReference.containsKey(playerReference)) {
			throw new IllegalArgumentException("Player already registered: " + playerReference.value());
		}

		if (this.playersBySeat.containsKey(seatNumber)) {
			throw new IllegalArgumentException("Seat is already claimed: " + seatNumber);
		}

		this.playersByReference.put(playerReference, validatedPlayerEntry);
		this.playersBySeat.put(seatNumber, validatedPlayerEntry);
		return validatedPlayerEntry;
	}

	public PlayerEntry claim(PlayerEntry playerEntry) {
		return this.register(playerEntry);
	}

	public Optional<PlayerEntry> getBySeatNumber(int seatNumber) {
		return Optional.ofNullable(this.playersBySeat.get(seatNumber));
	}

	public Optional<PlayerEntry> getByPlayerReference(PlayerReference playerReference) {
		return Optional.ofNullable(this.playersByReference.get(Objects.requireNonNull(playerReference, "playerReference")));
	}

	public Optional<PlayerEntry> unclaim(PlayerReference playerReference) {
		PlayerEntry removedEntry = this.playersByReference.remove(Objects.requireNonNull(playerReference, "playerReference"));
		if (removedEntry == null) {
			return Optional.empty();
		}

		this.playersBySeat.remove(removedEntry.getSeatNumber());
		return Optional.of(removedEntry);
	}

	public int getAliveCount() {
		return (int) this.playersByReference.values().stream()
				.filter(PlayerEntry::isAlive)
				.filter(playerEntry -> !playerEntry.isStoryteller())
				.count();
	}

	public Collection<PlayerEntry> getPlayers() {
		return Collections.unmodifiableCollection(this.playersByReference.values());
	}

	public boolean isClaimed(PlayerReference playerReference) {
		return this.playersByReference.containsKey(Objects.requireNonNull(playerReference, "playerReference"));
	}

	public void clear() {
		this.playersByReference.clear();
		this.playersBySeat.clear();
	}
}
