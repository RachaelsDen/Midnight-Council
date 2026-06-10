package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;

import java.util.Collections;
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

        if (nominator.equals(nominee)) {
            return false;
        }

        Optional<PlayerEntry> nominatorEntry = state.getPlayers().getByPlayerReference(nominator);
        Optional<PlayerEntry> nomineeEntry = state.getPlayers().getByPlayerReference(nominee);

        if (nominatorEntry.isEmpty() || nomineeEntry.isEmpty()) {
            return false;
        }

        if (!nominatorEntry.get().isAlive()) {
            return false;
        }

        if (nomineeEntry.get().isStoryteller()) {
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

        if (!state.getPlayers().getByPlayerReference(nominator).isPresent()) {
            throw new IllegalArgumentException("Nominator is not registered: " + nominator.value());
        }

        if (!state.getPlayers().getByPlayerReference(nominee).isPresent()) {
            throw new IllegalArgumentException("Nominee is not registered: " + nominee.value());
        }

        if (!canNominate(state, nominator, nominee)) {
            throw new IllegalStateException("Invalid nomination");
        }

        nominatorsToday.add(nominator);
        nominatorByNominee.put(nominee, nominator);
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

    public void resetForNewDay() {
        nominatorsToday.clear();
        nominatorByNominee.clear();
    }
}
