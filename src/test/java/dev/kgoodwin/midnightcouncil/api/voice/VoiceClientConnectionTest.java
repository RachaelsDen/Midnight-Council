package dev.kgoodwin.midnightcouncil.api.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class VoiceClientConnectionTest {

	@Test
	void isInterface() {
		assertTrue(VoiceClientConnection.class.isInterface());
	}

	@Test
	void hasGetPlayerIdMethod() throws NoSuchMethodException {
		Method m = VoiceClientConnection.class.getMethod("getPlayerId");
		assertTrue(m.getReturnType().getName().contains("PlayerReference"));
	}

	@Test
	void hasGetMicrophoneStateMethod() throws NoSuchMethodException {
		Method m = VoiceClientConnection.class.getMethod("getMicrophoneState");
		assertEquals(MicrophoneState.class, m.getReturnType());
	}

	@Test
	void hasSetMicrophoneStateMethod() throws NoSuchMethodException {
		VoiceClientConnection.class.getMethod("setMicrophoneState", MicrophoneState.class);
	}

	@Test
	void hasGetPositionMethod() throws NoSuchMethodException {
		Method m = VoiceClientConnection.class.getMethod("getPosition");
		assertTrue(m.getReturnType().getName().contains("Position"));
	}

	@Test
	void hasIsConnectedMethod() throws NoSuchMethodException {
		Method m = VoiceClientConnection.class.getMethod("isConnected");
		assertEquals(boolean.class, m.getReturnType());
	}

	@Test
	void hasSendPacketMethod() throws NoSuchMethodException {
		VoiceClientConnection.class.getMethod("sendPacket", AudioPacket.class);
	}

	@Test
	void hasGetLastPacketTimeMethod() throws NoSuchMethodException {
		Method m = VoiceClientConnection.class.getMethod("getLastPacketTime");
		assertEquals(long.class, m.getReturnType());
	}
}
