package dev.kgoodwin.midnightcouncil.api.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;

import org.junit.jupiter.api.Test;

class VoiceServerTest {

	@Test
	void isInterface() {
		assertTrue(VoiceServer.class.isInterface());
	}

	@Test
	void hasStartMethod() throws NoSuchMethodException {
		Method start = VoiceServer.class.getMethod("start", int.class);
		assertEquals(void.class, start.getReturnType());
	}

	@Test
	void hasStopMethod() throws NoSuchMethodException {
		Method stop = VoiceServer.class.getMethod("stop");
		assertEquals(void.class, stop.getReturnType());
	}

	@Test
	void hasConnectMethod() throws NoSuchMethodException {
		VoiceServer.class.getMethod("connect", VoiceClientConnection.class);
	}

	@Test
	void hasDisconnectMethod() throws NoSuchMethodException {
		VoiceServer.class.getMethod("disconnect", VoiceClientConnection.class);
	}

	@Test
	void hasGetConnectionsMethod() throws NoSuchMethodException {
		Method getConnections = VoiceServer.class.getMethod("getConnections");
		assertEquals(Collection.class, getConnections.getReturnType());
	}

	@Test
	void hasSendAudioMethod() throws NoSuchMethodException {
		VoiceServer.class.getMethod("sendAudio", AudioPacket.class);
	}

	@Test
	void hasIsRunningMethod() throws NoSuchMethodException {
		Method isRunning = VoiceServer.class.getMethod("isRunning");
		assertEquals(boolean.class, isRunning.getReturnType());
	}
}
