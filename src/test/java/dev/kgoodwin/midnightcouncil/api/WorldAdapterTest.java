package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldAdapterTest {

	@Test
	void worldAdapterDefinesWorldContracts() {
		assertTrue(WorldAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(WorldAdapter.class));
		assertMethodExists("setBlock", void.class, Position.class, String.class);
		assertMethodExists("clearBlock", void.class, Position.class);
		assertMethodExists("getBlockType", String.class, Position.class);
		assertMethodExists("spawnEntity", void.class, String.class, Position.class);
		assertMethodExists("getPlayerPosition", java.util.Optional.class, PlayerReference.class);
		assertMethodExists("playSound", void.class, Position.class, String.class, float.class, float.class);
		assertMethodExists("spawnParticles", void.class, String.class, Position.class, int.class);
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = WorldAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
