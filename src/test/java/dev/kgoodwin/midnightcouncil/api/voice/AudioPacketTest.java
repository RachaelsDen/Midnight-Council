package dev.kgoodwin.midnightcouncil.api.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;

class AudioPacketTest {

	@Test
	void recordConstructionAndFieldAccess() {
		PlayerReference sender = PlayerReference.ofName("Alice");
		byte[] data = {1, 2, 3, 4, 5};
		AudioPacket packet = new AudioPacket(sender, data, 42L, 1000L);

		assertEquals(sender, packet.senderId());
		assertArrayEquals(data, packet.encodedData());
		assertEquals(42L, packet.sequenceNumber());
		assertEquals(1000L, packet.timestamp());
		assertEquals(5, packet.length());
	}

	@Test
	void lengthReturnsDataLength() {
		AudioPacket packet = new AudioPacket(PlayerReference.ofName("Bob"), new byte[32], 1L, 0L);
		assertEquals(32, packet.length());
	}

	@Test
	void emptyDataIsAllowed() {
		AudioPacket packet = new AudioPacket(PlayerReference.ofName("Eve"), new byte[0], 0L, 0L);
		assertEquals(0, packet.length());
	}

	@Test
	void nullSenderIdThrows() {
		assertThrows(NullPointerException.class, () ->
			new AudioPacket(null, new byte[1], 0L, 0L));
	}

	@Test
	void nullEncodedDataThrows() {
		assertThrows(NullPointerException.class, () ->
			new AudioPacket(PlayerReference.ofName("Alice"), null, 0L, 0L));
	}
}
