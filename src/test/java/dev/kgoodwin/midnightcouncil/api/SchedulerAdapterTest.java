package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchedulerAdapterTest {

	@Test
	void schedulerAdapterDefinesTickSchedulingContracts() {
		assertTrue(SchedulerAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(SchedulerAdapter.class));
		assertMethodExists("runNextTick", void.class, Runnable.class);
		assertMethodExists("runAfterDelay", void.class, long.class, Runnable.class);
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = SchedulerAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
