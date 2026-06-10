package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NominationManager {

    private final GameEventDispatcher dispatcher;
    private final Set<PlayerReference> nominatorsToday = new java.util.HashSet<>();
    private final Map<PlayerReference, PlayerReference> nominatorByNominee = new LinkedHashMap<>();

    public NominationManager(GameEventDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public boolean canNominate(GameState state, PlayerReference nominator, PlayerReference nominee) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nominator, "nominator");
        Objects.requireNonNull(nominee, "nominee");

        Optional<PlayerEntry> nominatorEntry = state.getPlayers().getByPlayerReference(nominator);
        Optional<PlayerEntry> nomineeEntry = state.getPlayers().getByPlayerReference(nominee);

		if (nominatorEntry.isEmpty() || nomineeEntry.isEmpty()) {
			return false;
		}

		if (state.getPhase() != GamePhase.NOMINATION) {
			return false;
		}

		if (!nominatorEntry.get().isAlive()) {
			return false;
        }

        if (nominatorsToday.contains(nominator)) {
            return false;
        }

        if (nominatorByNominee.containsKey(nominee)) {
            return false;
        }

        return true;
    }

	public void nominate(GameState state, PlayerReference nominator, PlayerReference nominee) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(nominator, "nominator");
		Objects.requireNonNull(nominee, "nominee");

		if (state.getPhase() != GamePhase.NOMINATION) {
			throw new IllegalStateException("Nominations can only be made during NOMINATION phase");
		}

		if (!state.getPlayers().getByPlayerReference(nominator).isPresent()) {
			throw new IllegalArgumentException("Nominator is not registered: " + nominator.value());
        }

        if (!state.getPlayers().getByPlayerReference(nominee).isPresent()) {
            throw new IllegalArgumentException("Nominee is not registered: " + nominee.value());
        }

		if (!canNominate(state, nominator, nominee)) {
			throw new IllegalStateException("Invalid nomination");
		}

		int nomineeSeat = state.getPlayers().getByPlayerReference(nominee)
				.orElseThrow(() -> new IllegalArgumentException("Nominee is not registered: " + nominee.value()))
				.getSeatNumber();
		nominatorsToday.add(nominator);
		nominatorByNominee.put(nominee, nominator);
		state.setNominatedSeat(nomineeSeat);
		dispatcher.dispatch(new NominationOpened(nominator, nominee));
	}

    public boolean hasNominated(PlayerReference player) {
        return nominatorsToday.contains(Objects.requireNonNull(player, "player"));
    }

    public Optional<PlayerReference> getNominatorFor(PlayerReference nominee) {
        return Optional.ofNullable(nominatorByNominee.get(Objects.requireNonNull(nominee, "nominee")));
    }

    public int getNominationsToday() {
        return nominatorByNominee.size();
    }

	public void resetForNewDay(GameState state) {
		Objects.requireNonNull(state, "state");
		nominatorsToday.clear();
		nominatorByNominee.clear();
		state.clearNominatedSeat();
	}
}
