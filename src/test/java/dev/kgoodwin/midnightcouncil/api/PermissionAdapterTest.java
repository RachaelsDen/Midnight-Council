package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class PermissionAdapterTest {

	@Test
	void permissionAdapterDefinesStorytellerCheck() {
		assertTrue(PermissionAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(PermissionAdapter.class));
		assertMethodExists("isStoryteller", boolean.class, PlayerReference.class);
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = PermissionAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
