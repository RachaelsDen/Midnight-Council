package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceConnectionTest {

	private VoiceConnection connection;

	@BeforeEach
	void setUp() throws Exception {
		PlayerReference playerId = PlayerReference.ofName("test-player");
		javax.crypto.SecretKey key = generateAesKey();
		connection = new VoiceConnection(
			playerId,
			java.net.InetAddress.getLoopbackAddress(),
			12345,
			key,
			System.currentTimeMillis()
		);
	}

	@Test
	void getPlayerIdReturnsCorrectId() {
		assertEquals(PlayerReference.ofName("test-player"), connection.getPlayerId());
	}

	@Test
	void startsConnected() {
		assertTrue(connection.isConnected());
	}

	@Test
	void setConnectedFalse() {
		connection.setConnected(false);
		assertFalse(connection.isConnected());
	}

	@Test
	void defaultMicrophoneStateIsMuted() {
		assertEquals(MicrophoneState.MUTED, connection.getMicrophoneState());
	}

	@Test
	void setMicrophoneState() {
		connection.setMicrophoneState(MicrophoneState.ACTIVE);
		assertEquals(MicrophoneState.ACTIVE, connection.getMicrophoneState());
	}

	@Test
	void defaultPositionIsOrigin() {
		Position pos = connection.getPosition();
		assertEquals(0.0, pos.x());
		assertEquals(0.0, pos.y());
		assertEquals(0.0, pos.z());
	}

	@Test
	void setPosition() {
		Position newPos = new Position(10.5, 64.0, -3.2);
		connection.setPosition(newPos);
		assertEquals(newPos, connection.getPosition());
	}

	@Test
	void lastPacketTimeUpdatesWithMarkSeen() throws InterruptedException {
		long initial = connection.getLastPacketTime();
		Thread.sleep(10);
		long later = System.currentTimeMillis();
		connection.markSeen(later);
		assertTrue(connection.getLastPacketTime() >= initial);
		assertEquals(later, connection.getLastPacketTime());
	}

	@Test
	void lastPacketTimeFromConstructor() {
		long time = 123456789L;
		VoiceConnection vc = new VoiceConnection(
			PlayerReference.ofName("timed"),
			java.net.InetAddress.getLoopbackAddress(),
			9999,
			generateAesKey(),
			time
		);
		assertEquals(time, vc.getLastPacketTime());
	}

	private static javax.crypto.SecretKey generateAesKey() {
		try {
			javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("AES");
			kg.init(256);
			return kg.generateKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
