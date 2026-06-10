package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformInterfaceTest {

	@Test
	void platformInterfaceIsMarkerInterface() {
		assertTrue(PlatformInterface.class.isInterface());
		assertEquals(0, PlatformInterface.class.getDeclaredMethods().length);
	}
}
