package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerReferenceTest {

	@Test
	void playerReferenceIsRecordWithOpaqueValue() throws NoSuchMethodException {
		assertTrue(PlayerReference.class.isRecord());
		Method accessor = PlayerReference.class.getDeclaredMethod("value");
		assertEquals(String.class, accessor.getReturnType());
	}

	@Test
	void playerReferenceSupportsNameAndUuidFactories() {
		assertEquals("storyteller", PlayerReference.ofName("storyteller").value());
		UUID uuid = UUID.randomUUID();
		assertEquals(uuid.toString(), PlayerReference.ofUuid(uuid).value());
	}

	@Test
	void playerReferenceRejectsBlankValues() {
		assertInstanceOf(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class, () -> new PlayerReference(" ")));
	}
}
