package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class LoggerAdapterTest {

	@Test
	void loggerAdapterDefinesLoggingFacadeMethods() {
		assertTrue(LoggerAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(LoggerAdapter.class));
		assertMethodExists("debug", void.class, String.class);
		assertMethodExists("info", void.class, String.class);
		assertMethodExists("warn", void.class, String.class);
		assertMethodExists("error", void.class, String.class);
		assertMethodExists("error", void.class, String.class, Throwable.class);
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = LoggerAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
