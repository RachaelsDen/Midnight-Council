package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConfigAdapterTest {

	@Test
	void configAdapterDefinesLoadSaveAndLookupContracts() {
		assertTrue(ConfigAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(ConfigAdapter.class));
		assertMethodExists("load", void.class);
		assertMethodExists("save", void.class);
		assertMethodExists("get", java.util.Optional.class, String.class, Class.class);
		assertMethodExists("set", void.class, String.class, Object.class);
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = ConfigAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
