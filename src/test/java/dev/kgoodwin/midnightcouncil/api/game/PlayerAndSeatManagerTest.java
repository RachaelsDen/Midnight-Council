package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlayerAndSeatManagerTest {

	private GameEventDispatcher dispatcher;
	private PlayerAndSeatManager manager;

	@BeforeEach
	void setUp() {
		dispatcher = new GameEventDispatcher();
		manager = new PlayerAndSeatManager(dispatcher);
	}

	@Nested
	class JoinTests {

		@Test
		void playerJoinsAndGetsAutoAssignedSeat() {
			PlayerReference alice = PlayerReference.ofName("alice");
			PlayerEntry entry = manager.join(alice, "Alice", false);

			assertEquals(alice, entry.getPlayerReference());
			assertEquals("Alice", entry.getDisplayName());
			assertFalse(entry.isStoryteller());
			assertTrue(entry.isAlive());
			assertFalse(entry.isSleeping());
			assertTrue(manager.hasPlayer(alice));
			assertEquals(1, manager.getPlayerCount());
		}

		@Test
		void storytellerJoinsAndGetsSeatZero() {
			PlayerReference storyteller = PlayerReference.ofName("storyteller");
			PlayerEntry entry = manager.join(storyteller, "Storyteller", true);

			assertEquals(0, entry.getSeatNumber());
			assertTrue(entry.isStoryteller());
		}

		@Test
		void multiplePlayersJoinAndGetSequentialSeats() {
			PlayerReference p1 = PlayerReference.ofName("alice");
			PlayerReference p2 = PlayerReference.ofName("bob");
			PlayerReference p3 = PlayerReference.ofName("carol");

			PlayerEntry e1 = manager.join(p1, "Alice", false);
			PlayerEntry e2 = manager.join(p2, "Bob", false);
			PlayerEntry e3 = manager.join(p3, "Carol", false);

			assertEquals(1, e1.getSeatNumber());
			assertEquals(2, e2.getSeatNumber());
			assertEquals(3, e3.getSeatNumber());
			assertEquals(3, manager.getPlayerCount());
		}

		@Test
		void doubleJoinPrevented() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			assertThrows(IllegalArgumentException.class,
					() -> manager.join(alice, "Alice Again", false));
		}

		@Test
		void joinFiresPlayerStateChangedEvent() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			assertEquals(1, events.size());
			assertEquals(alice, events.getFirst().player());
			assertEquals("join", events.getFirst().changeType());
		}
	}

	@Nested
	class ClaimSeatTests {

		@Test
		void playerClaimsSpecificSeatNumber() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			PlayerEntry claimed = manager.claimSeat(alice, 3);

			assertEquals(3, claimed.getSeatNumber());
			assertEquals(alice, claimed.getPlayerReference());
		}

		@Test
		void claimSeatFiresEvent() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);
			events.clear();

			manager.claimSeat(alice, 2);

			assertEquals(1, events.size());
			assertEquals(alice, events.getFirst().player());
			assertEquals("seat_claim", events.getFirst().changeType());
		}

		@Test
		void cannotClaimAlreadyClaimedSeat() {
			PlayerReference alice = PlayerReference.ofName("alice");
			PlayerReference bob = PlayerReference.ofName("bob");
			manager.join(alice, "Alice", false);
			manager.join(bob, "Bob", false);
			manager.claimSeat(alice, 3);

			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(bob, 3));
		}

		@Test
		void cannotClaimSeatForUnregisteredPlayer() {
			PlayerReference ghost = PlayerReference.ofName("ghost");

			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(ghost, 1));
		}

		@Test
		void cannotClaimSeatOutsideValidRange() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(alice, 0));
			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(alice, 16));
			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(alice, -1));
		}

		@Test
		void storytellerCannotClaimPlayerSeat() {
			PlayerReference storyteller = PlayerReference.ofName("storyteller");
			manager.join(storyteller, "Storyteller", true);

			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(storyteller, 1));
		}

		@Test
		void claimSeatPreservesLifeAndSleepState() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);
			PlayerEntry existing = manager.getPlayer(alice).orElseThrow();
			existing.kill();
			existing.sleep();

			PlayerEntry claimed = manager.claimSeat(alice, 4);

			assertFalse(claimed.isAlive());
			assertTrue(claimed.isSleeping());
			assertEquals(LifeState.DEAD, claimed.getLifeState());
			assertEquals(SleepState.SLEEPING, claimed.getSleepState());
		}
	}

	@Nested
	class UnclaimSeatTests {

		@Test
		void playerUnclaimsSeat() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			Optional<PlayerEntry> unclaimed = manager.unclaimSeat(alice);

			assertTrue(unclaimed.isPresent());
			assertFalse(manager.hasPlayer(alice));
			assertEquals(0, manager.getPlayerCount());
		}

		@Test
		void unclaimSeatFiresEvent() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);
			events.clear();

			manager.unclaimSeat(alice);

			assertEquals(1, events.size());
			assertEquals(alice, events.getFirst().player());
			assertEquals("seat_unclaim", events.getFirst().changeType());
		}

		@Test
		void unclaimUnregisteredPlayerReturnsEmpty() {
			PlayerReference ghost = PlayerReference.ofName("ghost");

			Optional<PlayerEntry> result = manager.unclaimSeat(ghost);

			assertTrue(result.isEmpty());
		}

		@Test
		void unclaimSeatDoesNotFireEventForUnknownPlayer() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			manager.unclaimSeat(PlayerReference.ofName("ghost"));

			assertTrue(events.isEmpty());
		}
	}

	@Nested
	class LeaveTests {

		@Test
		void playerLeavesAndIsRemoved() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			Optional<PlayerEntry> left = manager.leave(alice);

			assertTrue(left.isPresent());
			assertEquals(alice, left.get().getPlayerReference());
			assertFalse(manager.hasPlayer(alice));
			assertEquals(0, manager.getPlayerCount());
		}

		@Test
		void leaveFiresEvent() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);
			events.clear();

			manager.leave(alice);

			assertEquals(1, events.size());
			assertEquals(alice, events.getFirst().player());
			assertEquals("leave", events.getFirst().changeType());
		}

		@Test
		void leaveUnregisteredPlayerReturnsEmpty() {
			PlayerReference ghost = PlayerReference.ofName("ghost");

			Optional<PlayerEntry> result = manager.leave(ghost);

			assertTrue(result.isEmpty());
		}

		@Test
		void leaveDoesNotFireEventForUnknownPlayer() {
			List<PlayerStateChanged> events = new ArrayList<>();
			dispatcher.registerListener(PlayerStateChanged.class, events::add);

			manager.leave(PlayerReference.ofName("ghost"));

			assertTrue(events.isEmpty());
		}
	}

	@Nested
	class CapacityAndRangeTests {

		@Test
		void canFillToMaxCapacity() {
			for (int i = 1; i <= 15; i++) {
				PlayerReference player = PlayerReference.ofName("player" + i);
				manager.join(player, "Player " + i, false);
			}

			assertEquals(15, manager.getPlayerCount());
		}

		@Test
		void cannotExceedMaxCapacity() {
			for (int i = 1; i <= 15; i++) {
				manager.join(PlayerReference.ofName("player" + i), "Player " + i, false);
			}

			assertThrows(IllegalArgumentException.class,
					() -> manager.join(PlayerReference.ofName("overflow"), "Overflow", false));
		}

		@Test
		void seatRangeMatchesLayoutPositions() {
			PlayerReference alice = PlayerReference.ofName("alice");
			manager.join(alice, "Alice", false);

			assertDoesNotThrow(() -> manager.claimSeat(alice, 13));
			assertThrows(IllegalArgumentException.class,
					() -> manager.claimSeat(alice, 16));
		}
	}

	@Nested
	class QueryTests {

		@Test
		void emptyManagerReturnsZeroCount() {
			assertEquals(0, manager.getPlayerCount());
			assertTrue(manager.getAllPlayers().isEmpty());
		}

		@Test
		void getPlayerReturnsEntryForRegisteredPlayer() {
			PlayerReference alice = PlayerReference.ofName("alice");
			PlayerEntry entry = manager.join(alice, "Alice", false);

			Optional<PlayerEntry> found = manager.getPlayer(alice);
			assertTrue(found.isPresent());
			assertEquals(entry, found.get());
		}

		@Test
		void getPlayerReturnsEmptyForUnknownPlayer() {
			Optional<PlayerEntry> found = manager.getPlayer(PlayerReference.ofName("nobody"));
			assertTrue(found.isEmpty());
		}

		@Test
		void getAllPlayersReturnsUnmodifiableCollection() {
			manager.join(PlayerReference.ofName("alice"), "Alice", false);

			Collection<PlayerEntry> players = manager.getAllPlayers();
			assertEquals(1, players.size());
			assertThrows(UnsupportedOperationException.class, () -> players.clear());
		}
	}
}
