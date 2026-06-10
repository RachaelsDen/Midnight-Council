package dev.kgoodwin.midnightcouncil.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VoiceConfigTest {

    @Test
    void testDefaultValues() {
        VoiceConfig config = new VoiceConfig();
        assertEquals(1.0, config.getMicVolume(), 0.001);
        assertEquals(1.0, config.getSpeakerVolume(), 0.001);
        assertEquals(-40.0, config.getActivationThreshold(), 0.001);
        assertEquals(VoiceConfig.InputMode.PTT, config.getInputMode());
        assertEquals("V", config.getPttKeybindName());
        assertFalse(config.isMuted());
    }

    @Test
    void testSaveAndLoad(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("test-voice.properties");
        VoiceConfig configToSave = new VoiceConfig();
        configToSave.setMicVolume(1.5);
        configToSave.setSpeakerVolume(0.8);
        configToSave.setActivationThreshold(-20.0);
        configToSave.setInputMode(VoiceConfig.InputMode.VOICE_ACTIVATION);
        configToSave.setPttKeybindName("X");
        configToSave.setMuted(true);

        configToSave.save(configFile);

        VoiceConfig loadedConfig = new VoiceConfig();
        loadedConfig.load(configFile);

        assertEquals(1.5, loadedConfig.getMicVolume(), 0.001);
        assertEquals(0.8, loadedConfig.getSpeakerVolume(), 0.001);
        assertEquals(-20.0, loadedConfig.getActivationThreshold(), 0.001);
        assertEquals(VoiceConfig.InputMode.VOICE_ACTIVATION, loadedConfig.getInputMode());
        assertEquals("X", loadedConfig.getPttKeybindName());
        assertTrue(loadedConfig.isMuted());
    }

    @Test
    void testValidation() {
        VoiceConfig config = new VoiceConfig();
        
        config.setMicVolume(3.0);
        config.setSpeakerVolume(-1.0);
        config.setActivationThreshold(-100.0);
        config.validate();

        assertEquals(2.0, config.getMicVolume(), 0.001);
        assertEquals(0.0, config.getSpeakerVolume(), 0.001);
        assertEquals(-80.0, config.getActivationThreshold(), 0.001);

        config.setMicVolume(-2.0);
        config.setSpeakerVolume(5.0);
        config.setActivationThreshold(20.0);
        config.validate();

        assertEquals(0.0, config.getMicVolume(), 0.001);
        assertEquals(2.0, config.getSpeakerVolume(), 0.001);
        assertEquals(0.0, config.getActivationThreshold(), 0.001);

        config.setPttKeybindName("");
        config.validate();
        assertEquals("V", config.getPttKeybindName());
    }
}
