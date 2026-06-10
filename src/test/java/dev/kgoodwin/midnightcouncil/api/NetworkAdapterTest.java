package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class NetworkAdapterTest {

	@Test
	void networkAdapterDefinesAudienceAwarePayloadContracts() {
		assertTrue(NetworkAdapter.class.isInterface());
		assertTrue(PlatformInterface.class.isAssignableFrom(NetworkAdapter.class));
		assertMethodExists("broadcastPublicPayload", void.class, String.class, byte[].class);
		assertMethodExists("sendStorytellerPayload", void.class, PlayerReference.class, String.class, byte[].class);
		assertMethodExists("registerReceiver", void.class, String.class, NetworkAdapter.PayloadHandler.class);
		assertTrue(NetworkAdapter.PayloadHandler.class.isInterface());
	}

	private static void assertMethodExists(String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			Method method = NetworkAdapter.class.getDeclaredMethod(name, parameterTypes);
			assertEquals(returnType, method.getReturnType());
		} catch (NoSuchMethodException exception) {
			throw new AssertionError("Missing method: " + name + List.of(parameterTypes), exception);
		}
	}
}
