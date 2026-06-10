package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class VoteManager {

	public enum VoteState {
		NOT_VOTED,
		VOTED_YES,
		VOTED_NO
	}

	private final GameEventDispatcher dispatcher;
	private final Map<PlayerReference, VoteState> voteStates = new LinkedHashMap<>();
	private final List<PlayerReference> voteOrder = new ArrayList<>();
	private PlayerReference nominee;
	private boolean voteInProgress;

	public VoteManager(GameEventDispatcher dispatcher) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public void startVote(GameState state, PlayerReference nominee) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(nominee, "nominee");

		if (voteInProgress) {
			throw new IllegalStateException("A vote is already in progress");
		}

		PlayerEntry nomineeEntry = state.getPlayers().getByPlayerReference(nominee)
				.orElseThrow(() -> new IllegalArgumentException("Nominee is not registered: " + nominee.value()));

		reset();

		this.nominee = nominee;

		List<PlayerEntry> eligible = state.getPlayers().getPlayers().stream()
				.filter(entry -> !entry.isStoryteller())
				.filter(PlayerEntry::isAlive)
				.filter(entry -> !entry.getPlayerReference().equals(nominee))
				.sorted(Comparator.comparingInt(PlayerEntry::getSeatNumber))
				.toList();

		for (PlayerEntry entry : eligible) {
			PlayerReference ref = entry.getPlayerReference();
			voteOrder.add(ref);
			voteStates.put(ref, VoteState.NOT_VOTED);
		}

		voteInProgress = true;

		if (voteOrder.isEmpty()) {
			resolveTally();
		}
	}

	public void castVote(PlayerReference voter, boolean yes) {
		Objects.requireNonNull(voter, "voter");

		if (!voteInProgress) {
			throw new IllegalStateException("No vote is currently in progress");
		}

		VoteState current = voteStates.get(voter);
		if (current == null) {
			throw new IllegalArgumentException("Player is not an eligible voter: " + voter.value());
		}

		if (current != VoteState.NOT_VOTED) {
			throw new IllegalStateException("Player has already voted: " + voter.value());
		}

		voteStates.put(voter, yes ? VoteState.VOTED_YES : VoteState.VOTED_NO);

		if (allVoted()) {
			resolveTally();
		}
	}

	public VoteState getVoteState(PlayerReference player) {
		return voteStates.getOrDefault(Objects.requireNonNull(player, "player"), VoteState.NOT_VOTED);
	}

	public boolean isVoteInProgress() {
		return voteInProgress;
	}

	public Optional<PlayerReference> getNominee() {
		return Optional.ofNullable(nominee);
	}

	public List<PlayerReference> getVoteOrder() {
		return Collections.unmodifiableList(voteOrder);
	}

	public void reset() {
		voteStates.clear();
		voteOrder.clear();
		nominee = null;
		voteInProgress = false;
	}

	private boolean allVoted() {
		for (VoteState vs : voteStates.values()) {
			if (vs == VoteState.NOT_VOTED) {
				return false;
			}
		}
		return true;
	}

	private void resolveTally() {
		int yesCount = 0;
		for (VoteState vs : voteStates.values()) {
			if (vs == VoteState.VOTED_YES) {
				yesCount++;
			}
		}

		int eligibleCount = voteOrder.size();
		int threshold = eligibleCount > 0 ? (eligibleCount / 2) + 1 : 0;

		dispatcher.dispatch(new VoteResolved(nominee, yesCount, threshold));
		voteInProgress = false;
	}
}
