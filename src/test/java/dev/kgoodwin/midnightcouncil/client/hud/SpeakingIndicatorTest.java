package dev.kgoodwin.midnightcouncil.client.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakingIndicatorTest {

    private SpeakingIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = SpeakingIndicator.getInstance();
        indicator.clear();
    }

    @Test
    void testSetSpeakingAndIsSpeaking() {
        assertFalse(indicator.isSpeaking("Player1"));

        indicator.setSpeaking("Player1", true);
        assertTrue(indicator.isSpeaking("Player1"));

        indicator.setSpeaking("Player1", false);
        assertFalse(indicator.isSpeaking("Player1"));
    }

    @Test
    void testMultiplePlayers() {
        indicator.setSpeaking("Player1", true);
        indicator.setSpeaking("Player2", true);

        assertTrue(indicator.isSpeaking("Player1"));
        assertTrue(indicator.isSpeaking("Player2"));
        assertFalse(indicator.isSpeaking("Player3"));
    }

    @Test
    void testClear() {
        indicator.setSpeaking("Player1", true);
        indicator.clear();
        assertFalse(indicator.isSpeaking("Player1"));
    }
}
