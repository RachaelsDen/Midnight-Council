package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.seating.SeatLayouts;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class PlayerAndSeatManager {

	private static final int MIN_PLAYERS = 5;
	private static final int MAX_PLAYERS = 12;

	private final PlayerRegistry registry;
	private final GameEventDispatcher dispatcher;

	public PlayerAndSeatManager(GameEventDispatcher dispatcher) {
		this.registry = new PlayerRegistry();
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public PlayerEntry join(PlayerReference player, String displayName, boolean isStoryteller) {
		Objects.requireNonNull(player, "player");
		if (registry.isClaimed(player)) {
			throw new IllegalArgumentException("Player already joined: " + player.value());
		}

		int nonStorytellerCount = countNonStorytellers();
		if (!isStoryteller && nonStorytellerCount >= MAX_PLAYERS) {
			throw new IllegalArgumentException("Maximum player capacity reached");
		}

		int seatNumber;
		if (isStoryteller) {
			seatNumber = 0;
		} else {
			seatNumber = findNextAvailableSeat();
		}

		PlayerEntry entry = new PlayerEntry(seatNumber, displayName, isStoryteller, player);
		registry.register(entry);
		dispatcher.dispatch(new PlayerStateChanged(player, "join"));
		return entry;
	}

	public PlayerEntry claimSeat(PlayerReference player, int seatNumber) {
		Objects.requireNonNull(player, "player");
		PlayerEntry existing = registry.getByPlayerReference(player)
				.orElseThrow(() -> new IllegalArgumentException("Player is not registered: " + player.value()));
		if (existing.isStoryteller()) {
			throw new IllegalArgumentException("Storytellers cannot claim player seats");
		}

		validateSeatNumber(seatNumber);

		Optional<PlayerEntry> occupant = registry.getBySeatNumber(seatNumber);
		if (occupant.isPresent() && !occupant.get().getPlayerReference().equals(player)) {
			throw new IllegalArgumentException("Seat is already claimed: " + seatNumber);
		}

		registry.unclaim(player);
		PlayerEntry newEntry = new PlayerEntry(seatNumber, existing.getDisplayName(),
				existing.getLifeState(), existing.getSleepState(), existing.isStoryteller(), player);
		registry.register(newEntry);
		dispatcher.dispatch(new PlayerStateChanged(player, "seat_claim"));
		return newEntry;
	}

	public Optional<PlayerEntry> unclaimSeat(PlayerReference player) {
		Objects.requireNonNull(player, "player");
		Optional<PlayerEntry> removed = registry.unclaim(player);
		if (removed.isPresent()) {
			dispatcher.dispatch(new PlayerStateChanged(player, "seat_unclaim"));
		}
		return removed;
	}

	public Optional<PlayerEntry> leave(PlayerReference player) {
		Objects.requireNonNull(player, "player");
		Optional<PlayerEntry> removed = registry.unclaim(player);
		if (removed.isPresent()) {
			dispatcher.dispatch(new PlayerStateChanged(player, "leave"));
		}
		return removed;
	}

	public Optional<PlayerEntry> getPlayer(PlayerReference player) {
		return registry.getByPlayerReference(Objects.requireNonNull(player, "player"));
	}

	public Collection<PlayerEntry> getAllPlayers() {
		return registry.getPlayers();
	}

	public int getPlayerCount() {
		return registry.getPlayers().size();
	}

	public boolean hasPlayer(PlayerReference player) {
		return registry.isClaimed(Objects.requireNonNull(player, "player"));
	}

	private int findNextAvailableSeat() {
		for (int seat = 1; seat <= MAX_PLAYERS; seat++) {
			if (registry.getBySeatNumber(seat).isEmpty()) {
				return seat;
			}
		}
		throw new IllegalStateException("No available seats");
	}

	private void validateSeatNumber(int seatNumber) {
		if (seatNumber < 1 || seatNumber > MAX_PLAYERS) {
			throw new IllegalArgumentException("Seat number out of valid range: " + seatNumber);
		}
	}

	private int countNonStorytellers() {
		return (int) registry.getPlayers().stream()
				.filter(entry -> !entry.isStoryteller())
				.count();
	}
}
