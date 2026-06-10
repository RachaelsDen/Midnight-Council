package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PositionTest {

	@Test
	void positionIsPureJavaRecord() throws NoSuchMethodException {
		assertTrue(Position.class.isRecord());
		assertEquals(double.class, Position.class.getDeclaredMethod("x").getReturnType());
		assertEquals(double.class, Position.class.getDeclaredMethod("y").getReturnType());
		assertEquals(double.class, Position.class.getDeclaredMethod("z").getReturnType());
	}

	@Test
	void positionPreservesCoordinates() {
		Position position = new Position(1.25D, 64.0D, -3.5D);
		assertEquals(1.25D, position.x());
		assertEquals(64.0D, position.y());
		assertEquals(-3.5D, position.z());
	}
}
