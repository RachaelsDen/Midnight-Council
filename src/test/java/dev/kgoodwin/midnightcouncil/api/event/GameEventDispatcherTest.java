package dev.kgoodwin.midnightcouncil.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import org.junit.jupiter.api.Test;

class GameEventDispatcherTest {

	@Test
	void dispatchCallsAllHandlersForMatchingType() {
		GameEventDispatcher dispatcher = new GameEventDispatcher();
		List<PhaseChanged> received = new ArrayList<>();
		dispatcher.registerListener(PhaseChanged.class, received::add);
		dispatcher.registerListener(PhaseChanged.class, received::add);

		dispatcher.dispatch(new PhaseChanged(GamePhase.IDLE, GamePhase.SETUP));

		assertEquals(2, received.size());
		assertEquals(GamePhase.IDLE, received.getFirst().oldPhase());
	}

	@Test
	void dispatchDoesNotCallHandlersForDifferentType() {
		GameEventDispatcher dispatcher = new GameEventDispatcher();
		List<PhaseChanged> received = new ArrayList<>();
		dispatcher.registerListener(PhaseChanged.class, received::add);

		dispatcher.dispatch(new NominationOpened(PlayerReference.ofName("nominator"), PlayerReference.ofName("nominee")));

		assertTrue(received.isEmpty());
	}

	@Test
	void dispatchWithNoHandlersDoesNotThrow() {
		GameEventDispatcher dispatcher = new GameEventDispatcher();
		dispatcher.dispatch(new ExecutionResolved(PlayerReference.ofName("alice"), "lynch"));
	}

	@Test
	void handlerOrderingIsRegistrationOrder() {
		GameEventDispatcher dispatcher = new GameEventDispatcher();
		List<Integer> order = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, e -> order.add(1));
		dispatcher.registerListener(VoteResolved.class, e -> order.add(2));
		dispatcher.registerListener(VoteResolved.class, e -> order.add(3));

		dispatcher.dispatch(new VoteResolved(PlayerReference.ofName("target"), 3, 5));

		assertEquals(List.of(1, 2, 3), order);
	}

	@Test
	void oneFailingHandlerDoesNotBlockSubsequent() {
		GameEventDispatcher dispatcher = new GameEventDispatcher();
		List<String> calls = new ArrayList<>();
		dispatcher.registerListener(NominationOpened.class, e -> {
			calls.add("first");
			throw new RuntimeException("boom");
		});
		dispatcher.registerListener(NominationOpened.class, e -> calls.add("second"));

		dispatcher.dispatch(new NominationOpened(PlayerReference.ofName("nominator"), PlayerReference.ofName("nominee")));

		assertEquals(List.of("first", "second"), calls);
	}
}
