package dev.kgoodwin.midnightcouncil.api.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.game.GameState;

import java.lang.reflect.Method;
import java.util.Collection;

import org.junit.jupiter.api.Test;

class VoiceRoutingStrategyTest {

	@Test
	void isInterface() {
		assertTrue(VoiceRoutingStrategy.class.isInterface());
	}

	@Test
	void hasRouteMethod() throws NoSuchMethodException {
		Method route = VoiceRoutingStrategy.class.getMethod(
			"route", VoiceServer.class, AudioPacket.class, GameState.class);
		assertEquals(Collection.class, route.getReturnType());
	}
}
