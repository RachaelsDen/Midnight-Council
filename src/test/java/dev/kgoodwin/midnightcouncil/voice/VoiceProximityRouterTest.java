package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VoiceProximityRouterTest {

	private static final double DEFAULT_DISTANCE = 32.0;

	private TestServer server;
	private GameState state;

	@BeforeEach
	void setUp() {
		server = new TestServer();
		state = new GameState();
	}

	private VoiceProximityRouter defaultRouter() {
		return new VoiceProximityRouter(DEFAULT_DISTANCE);
	}

	private PlayerReference ref(String name) {
		return PlayerReference.ofName(name);
	}

	private TestConnection connection(String name, double x, double y, double z) {
		return new TestConnection(ref(name), new Position(x, y, z));
	}

	private AudioPacket packet(String senderName) {
		return new AudioPacket(ref(senderName), new byte[]{1, 2, 3}, 1L, System.currentTimeMillis());
	}

	private int seatCounter = 0;

	private void registerPlayer(String name) {
		registerPlayer(name, LifeState.ALIVE, SleepState.AWAKE, false);
	}

	private void registerPlayer(String name, LifeState life, SleepState sleep, boolean storyteller) {
		PlayerEntry entry = new PlayerEntry(
				seatCounter++,
				name,
				life,
				sleep,
				storyteller,
				ref(name));
		state.getPlayers().register(entry);
	}

	private void setPhase(GamePhase target) {
		GamePhase current = state.getPhase();
		if (current == GamePhase.IDLE && target != GamePhase.IDLE) {
			if (target == GamePhase.SETUP) {
				state.setPhase(GamePhase.SETUP);
			} else if (target == GamePhase.SEATING) {
				state.setPhase(GamePhase.SETUP);
				state.setPhase(GamePhase.SEATING);
			} else if (target == GamePhase.DAY) {
				state.setPhase(GamePhase.SETUP);
				state.setPhase(GamePhase.SEATING);
				state.setPhase(GamePhase.DAY);
			} else if (target == GamePhase.NIGHT) {
				state.setPhase(GamePhase.SETUP);
				state.setPhase(GamePhase.SEATING);
				state.setPhase(GamePhase.NIGHT);
			}
		} else if (current == GamePhase.DAY && target == GamePhase.NIGHT) {
			state.setPhase(GamePhase.NIGHT);
		} else if (current == GamePhase.NIGHT && target == GamePhase.DAY) {
			state.setPhase(GamePhase.DAY);
		} else if (current == GamePhase.SETUP && target == GamePhase.SEATING) {
			state.setPhase(GamePhase.SEATING);
		} else if (current == GamePhase.SEATING && target == GamePhase.DAY) {
			state.setPhase(GamePhase.DAY);
		} else if (current == GamePhase.SEATING && target == GamePhase.NIGHT) {
			state.setPhase(GamePhase.NIGHT);
		}
	}

	@Nested
	class ImplementsInterface {
		@Test
		void implementsVoiceRoutingStrategy() {
			assertTrue(new VoiceProximityRouter(DEFAULT_DISTANCE) instanceof VoiceRoutingStrategy);
		}
	}

	@Nested
	class DayPhase {

		@BeforeEach
		void setUpDay() {
			setPhase(GamePhase.DAY);
		}

		@Test
		void twoAlivePlayersWithinDistanceHearEachOther() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 20, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertEquals(1, recipients.size());
			assertTrue(recipients.contains(bob));
		}

		@Test
		void twoPlayersOutsideDistanceDontHearEachOther() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 50, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.isEmpty());
		}

		@Test
		void deadPlayerCanListenDuringDayButNotSend() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);

			Collection<VoiceClientConnection> recipientsWhenDeadSends = defaultRouter().route(server, packet("Bob"), state);
			assertTrue(recipientsWhenDeadSends.isEmpty());

			Collection<VoiceClientConnection> recipientsWhenAliveSends = defaultRouter().route(server, packet("Alice"), state);
			assertTrue(recipientsWhenAliveSends.contains(deadBob));
		}

		@Test
		void sleepingPlayerCanHearDuringDay() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection sleepingBob = connection("Bob", 5, 0, 0);
			sleepingBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(sleepingBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.ALIVE, SleepState.SLEEPING, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(sleepingBob));
		}

		@Test
		void sleepingPlayerCanSendDuringDay() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection sleepingBob = connection("Bob", 5, 0, 0);
			sleepingBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(sleepingBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.ALIVE, SleepState.SLEEPING, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.contains(alice));
		}
	}

	@Nested
	class NightPhase {

		@BeforeEach
		void setUpNight() {
			setPhase(GamePhase.NIGHT);
		}

		@Test
		void deadPlayerCantHearDuringNight() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertFalse(recipients.contains(deadBob));
		}

		@Test
		void sleepingPlayerMutedDuringNight() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection sleepingBob = connection("Bob", 5, 0, 0);
			sleepingBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(sleepingBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.ALIVE, SleepState.SLEEPING, false);

			Collection<VoiceClientConnection> recipientsFromAlice = defaultRouter().route(server, packet("Alice"), state);
			assertFalse(recipientsFromAlice.contains(sleepingBob));

			Collection<VoiceClientConnection> recipientsFromBob = defaultRouter().route(server, packet("Bob"), state);
			assertFalse(recipientsFromBob.contains(alice));
		}

		@Test
		void aliveAwakePlayersHearEachOtherDuringNight() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 10, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void unregisteredRecipientCannotHearDuringNight() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 10, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertFalse(recipients.contains(bob));
		}
	}

	@Nested
	class PreGame {

		@Test
		void everyoneHearsEveryoneWithinDistanceDuringSetup() {
			setPhase(GamePhase.SETUP);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 10, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void everyoneHearsEveryoneWithinDistanceDuringSeating() {
			setPhase(GamePhase.SEATING);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 10, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void everyoneHearsEveryoneWithinDistanceDuringIdle() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 10, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void deadAndSleepingHearDuringIdle() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection sleepingCarol = connection("Carol", 8, 0, 0);
			sleepingCarol.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			server.add(sleepingCarol);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);
			registerPlayer("Carol", LifeState.ALIVE, SleepState.SLEEPING, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(deadBob));
			assertTrue(recipients.contains(sleepingCarol));
		}

		@Test
		void deadPlayerCanSendDuringIdle() {
			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.contains(alice));
		}

		@Test
		void deadPlayerCanSendDuringSetup() {
			setPhase(GamePhase.SETUP);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.contains(alice));
		}

		@Test
		void deadPlayerCanSendDuringSeating() {
			setPhase(GamePhase.SEATING);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection deadBob = connection("Bob", 5, 0, 0);
			deadBob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(deadBob);
			registerPlayer("Alice");
			registerPlayer("Bob", LifeState.DEAD, SleepState.AWAKE, false);

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.contains(alice));
		}

		@Test
		void unregisteredSenderCanSendDuringSetup() {
			setPhase(GamePhase.SETUP);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.contains(alice));
		}

		@Test
		void unregisteredRecipientCanHearDuringSetup() {
			setPhase(GamePhase.SETUP);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}
	}

	@Nested
	class SenderRules {

		@Test
		void mutedSenderDoesNotReachAnyone() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.MUTED);
			TestConnection bob = connection("Bob", 5, 0, 0);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.isEmpty());
		}

		@Test
		void noLoopbackSenderExcluded() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertFalse(recipients.contains(alice));
			assertTrue(recipients.contains(bob));
		}

		@Test
		void pushToTalkSenderCanSend() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.PUSH_TO_TALK);
			TestConnection bob = connection("Bob", 5, 0, 0);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void unregisteredSenderCannotSendDuringDay() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Bob"), state);

			assertTrue(recipients.isEmpty());
		}

		@Test
		void disconnectedRecipientCannotHearDuringDay() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);
			bob.setConnected(false);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertFalse(recipients.contains(bob));
		}

		@Test
		void disconnectedSenderDoesNotReachAnyone() {
			setPhase(GamePhase.DAY);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			alice.setConnected(false);
			TestConnection bob = connection("Bob", 5, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = defaultRouter().route(server, packet("Alice"), state);

			assertTrue(recipients.isEmpty());
		}
	}

	@Nested
	class Distance {

		@Test
		void rejectsNaNMaxDistance() {
			assertThrows(IllegalArgumentException.class, () -> new VoiceProximityRouter(Double.NaN));
		}

		@Test
		void rejectsInfiniteMaxDistance() {
			assertThrows(IllegalArgumentException.class,
					() -> new VoiceProximityRouter(Double.POSITIVE_INFINITY));
		}

		@Test
		void configurableMaxDistance() {
			setPhase(GamePhase.DAY);

			VoiceProximityRouter router = new VoiceProximityRouter(10.0);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 15, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = router.route(server, packet("Alice"), state);

			assertTrue(recipients.isEmpty());
		}

		@Test
		void exactBoundaryDistanceIncluded() {
			setPhase(GamePhase.DAY);

			VoiceProximityRouter router = new VoiceProximityRouter(32.0);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 32, 0, 0);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = router.route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void distanceUses3DCoordinates() {
			setPhase(GamePhase.DAY);

			VoiceProximityRouter router = new VoiceProximityRouter(10.0);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 6, 0, 8);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = router.route(server, packet("Alice"), state);

			assertTrue(recipients.contains(bob));
		}

		@Test
		void justBeyondBoundaryExcluded() {
			setPhase(GamePhase.DAY);

			VoiceProximityRouter router = new VoiceProximityRouter(10.0);

			TestConnection alice = connection("Alice", 0, 0, 0);
			alice.setMicrophoneState(MicrophoneState.ACTIVE);
			TestConnection bob = connection("Bob", 6, 0, 9);
			bob.setMicrophoneState(MicrophoneState.ACTIVE);

			server.add(alice);
			server.add(bob);
			registerPlayer("Alice");
			registerPlayer("Bob");

			Collection<VoiceClientConnection> recipients = router.route(server, packet("Alice"), state);

			assertFalse(recipients.contains(bob));
		}
	}

	static class TestServer implements VoiceServer {

		private final List<VoiceClientConnection> connections = new ArrayList<>();
		private boolean running;

		void add(VoiceClientConnection conn) {
			connections.add(conn);
		}

		@Override
		public void start(int port) {
			running = true;
		}

		@Override
		public void stop() {
			running = false;
		}

		@Override
		public void connect(VoiceClientConnection connection) {
			connections.add(connection);
		}

		@Override
		public void disconnect(VoiceClientConnection connection) {
			connections.remove(connection);
		}

		@Override
		public Collection<VoiceClientConnection> getConnections() {
			return connections;
		}

		@Override
		public void sendAudio(AudioPacket packet) {
		}

		@Override
		public boolean isRunning() {
			return running;
		}
	}

	static class TestConnection implements VoiceClientConnection {

		private final PlayerReference playerId;
		private final Position position;
		private MicrophoneState micState = MicrophoneState.MUTED;
		private boolean connected = true;
		private long lastPacketTime = 0;

		TestConnection(PlayerReference playerId, Position position) {
			this.playerId = playerId;
			this.position = position;
		}

		public void setMicrophoneState(MicrophoneState state) {
			this.micState = state;
		}

		@Override
		public PlayerReference getPlayerId() {
			return playerId;
		}

		@Override
		public MicrophoneState getMicrophoneState() {
			return micState;
		}

		@Override
		public Position getPosition() {
			return position;
		}

		@Override
		public boolean isConnected() {
			return connected;
		}

		void setConnected(boolean connected) {
			this.connected = connected;
		}

		@Override
		public void sendPacket(AudioPacket packet) {
			this.lastPacketTime = System.currentTimeMillis();
		}

		@Override
		public long getLastPacketTime() {
			return lastPacketTime;
		}
	}
}
