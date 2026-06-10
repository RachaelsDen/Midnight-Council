package dev.kgoodwin.midnightcouncil.api.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MicrophoneStateTest {

	@Test
	void hasExpectedValues() {
		MicrophoneState[] values = MicrophoneState.values();
		assertEquals(3, values.length);
		assertNotNull(MicrophoneState.valueOf("MUTED"));
		assertNotNull(MicrophoneState.valueOf("ACTIVE"));
		assertNotNull(MicrophoneState.valueOf("PUSH_TO_TALK"));
	}
}
