package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NominationManagerTest {

	private GameEventDispatcher dispatcher;
	private GameState state;
	private NominationManager manager;
	private PlayerReference alice;
	private PlayerReference bob;
	private PlayerReference carol;
	private PlayerReference storyteller;

	@BeforeEach
	void setUp() {
		dispatcher = new GameEventDispatcher();
		manager = new NominationManager(dispatcher);
		state = new GameState();
		state.setPhase(GamePhase.SETUP);
		state.setPhase(GamePhase.SEATING);
		state.setPhase(GamePhase.DAY);

		alice = PlayerReference.ofName("alice");
		bob = PlayerReference.ofName("bob");
		carol = PlayerReference.ofName("carol");
		storyteller = PlayerReference.ofName("storyteller");

		state.getPlayers().register(new PlayerEntry(0, "Alice", false, alice));
		state.getPlayers().register(new PlayerEntry(1, "Bob", false, bob));
		state.getPlayers().register(new PlayerEntry(2, "Carol", false, carol));
		state.getPlayers().register(new PlayerEntry(3, "Storyteller", true, storyteller));
	}

	@Test
	void canNominate_validPair_returnsTrue() {
		assertTrue(manager.canNominate(state, alice, bob));
	}

	@Test
	void nominate_firesNominationOpenedEvent() {
		List<NominationOpened> events = new ArrayList<>();
		dispatcher.registerListener(NominationOpened.class, events::add);

		manager.nominate(state, alice, bob);

		assertEquals(1, events.size());
		assertEquals(alice, events.getFirst().nominator());
		assertEquals(bob, events.getFirst().nominee());
	}

	@Test
	void hasNominated_returnsFalseBeforeNomination() {
		assertFalse(manager.hasNominated(alice));
	}

	@Test
	void hasNominated_returnsTrueAfterNomination() {
		manager.nominate(state, alice, bob);

		assertTrue(manager.hasNominated(alice));
	}

	@Test
	void eachPlayerCanNominateOncePerDay() {
		manager.nominate(state, alice, bob);

		assertFalse(manager.canNominate(state, alice, carol));
	}

	@Test
	void differentPlayersCanEachNominate() {
		manager.nominate(state, alice, bob);

		assertTrue(manager.canNominate(state, bob, carol));
	}

	@Test
	void deadPlayerCannotNominate() {
		state.getPlayers().getByPlayerReference(alice).get().kill();

		assertFalse(manager.canNominate(state, alice, bob));
	}

	@Test
	void deadPlayerCanBeNominated() {
		state.getPlayers().getByPlayerReference(bob).get().kill();

		assertTrue(manager.canNominate(state, alice, bob));
	}

	@Test
	void playerCanNominateThemselves() {
		assertTrue(manager.canNominate(state, alice, alice));
	}

	@Test
	void canNominateStoryteller() {
		assertTrue(manager.canNominate(state, alice, storyteller));
	}

	@Test
	void cannotNominateAlreadyNominatedToday() {
		manager.nominate(state, alice, bob);

		assertFalse(manager.canNominate(state, carol, bob));
	}

	@Test
	void nominate_throwsWhenInvalid() {
		state.getPlayers().getByPlayerReference(alice).get().kill();
		assertThrows(IllegalStateException.class, () -> manager.nominate(state, alice, bob));
	}

	@Test
	void getNominatorFor_returnsNominator() {
		manager.nominate(state, alice, bob);

		assertTrue(manager.getNominatorFor(bob).isPresent());
		assertEquals(alice, manager.getNominatorFor(bob).get());
	}

	@Test
	void getNominatorFor_returnsEmptyForUnnominated() {
		assertTrue(manager.getNominatorFor(bob).isEmpty());
	}

	@Test
	void getNominationsToday_initiallyZero() {
		assertEquals(0, manager.getNominationsToday());
	}

	@Test
	void getNominationsToday_incrementsAfterNomination() {
		manager.nominate(state, alice, bob);
		assertEquals(1, manager.getNominationsToday());

		manager.nominate(state, bob, carol);
		assertEquals(2, manager.getNominationsToday());
	}

	@Test
	void resetForNewDay_clearsTracking() {
		manager.nominate(state, alice, bob);
		assertEquals(1, manager.getNominationsToday());
		assertTrue(manager.hasNominated(alice));

		manager.resetForNewDay();

		assertEquals(0, manager.getNominationsToday());
		assertFalse(manager.hasNominated(alice));
		assertTrue(manager.getNominatorFor(bob).isEmpty());
	}

	@Test
	void afterResetPlayerCanNominateAgain() {
		manager.nominate(state, alice, bob);
		manager.resetForNewDay();

		assertTrue(manager.canNominate(state, alice, bob));
	}

	@Test
	void nominate_nullStateThrows() {
		assertThrows(NullPointerException.class,
				() -> manager.nominate(null, alice, bob));
	}

	@Test
	void nominate_nullNominatorThrows() {
		assertThrows(NullPointerException.class,
				() -> manager.nominate(state, null, bob));
	}

	@Test
	void nominate_nullNomineeThrows() {
		assertThrows(NullPointerException.class,
				() -> manager.nominate(state, alice, null));
	}

	@Test
	void nominate_unregisteredNominatorThrows() {
		PlayerReference unknown = PlayerReference.ofName("unknown");
		assertThrows(IllegalArgumentException.class,
				() -> manager.nominate(state, unknown, bob));
	}

	@Test
	void nominate_unregisteredNomineeThrows() {
		PlayerReference unknown = PlayerReference.ofName("unknown");
		assertThrows(IllegalArgumentException.class,
				() -> manager.nominate(state, alice, unknown));
	}
}
