package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PacketTypeTest {

	@Test
	void audioTypeHasCorrectId() {
		assertEquals((byte) 0x1, PacketType.AUDIO.id);
	}

	@Test
	void connectTypeHasCorrectId() {
		assertEquals((byte) 0x2, PacketType.CONNECT.id);
	}

	@Test
	void disconnectTypeHasCorrectId() {
		assertEquals((byte) 0x3, PacketType.DISCONNECT.id);
	}

	@Test
	void keepaliveTypeHasCorrectId() {
		assertEquals((byte) 0x4, PacketType.KEEPALIVE.id);
	}

	@Test
	void fromIdReturnsCorrectType() {
		assertEquals(PacketType.AUDIO, PacketType.fromId((byte) 0x1));
		assertEquals(PacketType.CONNECT, PacketType.fromId((byte) 0x2));
		assertEquals(PacketType.DISCONNECT, PacketType.fromId((byte) 0x3));
		assertEquals(PacketType.KEEPALIVE, PacketType.fromId((byte) 0x4));
	}

	@Test
	void fromIdThrowsOnUnknownType() {
		assertThrows(IllegalArgumentException.class, () -> PacketType.fromId((byte) 0xFF));
	}

	@Test
	void fromIdSafeReturnsNullForUnknownType() {
		assertNull(PacketType.fromIdSafe((byte) 0xFF));
		assertNull(PacketType.fromIdSafe((byte) 0x00));
	}

	@Test
	void fromIdSafeReturnsCorrectTypeForKnownIds() {
		for (PacketType t : PacketType.values()) {
			assertNotNull(PacketType.fromIdSafe(t.id));
			assertEquals(t, PacketType.fromIdSafe(t.id));
		}
	}

	@Test
	void allTypesHaveUniqueIds() {
		PacketType[] types = PacketType.values();
		for (int i = 0; i < types.length; i++) {
			for (int j = i + 1; j < types.length; j++) {
				if (types[i].id == types[j].id) {
					throw new AssertionError("Duplicate id: " + types[i].id);
				}
			}
		}
	}
}
