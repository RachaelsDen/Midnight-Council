package dev.kgoodwin.midnightcouncil.client.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class VoiceConfig {
    public enum InputMode {
        PTT, VOICE_ACTIVATION
    }

    private double micVolume = 1.0;
    private double speakerVolume = 1.0;
    private double activationThreshold = -40.0;
    private InputMode inputMode = InputMode.PTT;
    private String pttKeybindName = "V";
    private boolean muted = false;

    private static VoiceConfig instance;

    public static VoiceConfig getInstance() {
        if (instance == null) {
            instance = new VoiceConfig();
            instance.load();
        }
        return instance;
    }

    public VoiceConfig() {}

    private Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("midnightcouncil-voice.properties");
    }

    public void load() {
        load(getConfigFile());
    }

    public void load(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);

            this.micVolume = Double.parseDouble(props.getProperty("micVolume", "1.0"));
            this.speakerVolume = Double.parseDouble(props.getProperty("speakerVolume", "1.0"));
            this.activationThreshold = Double.parseDouble(props.getProperty("activationThreshold", "-40.0"));
            try {
                this.inputMode = InputMode.valueOf(props.getProperty("inputMode", "PTT"));
            } catch (IllegalArgumentException e) {
                this.inputMode = InputMode.PTT;
            }
            this.pttKeybindName = props.getProperty("pttKeybindName", "V");
            this.muted = Boolean.parseBoolean(props.getProperty("muted", "false"));

            validate();
        } catch (Exception e) {
            System.err.println("Failed to load VoiceConfig: " + e.getMessage());
        }
    }

    public void save() {
        save(getConfigFile());
    }

    public void save(Path file) {
        validate();
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                Properties props = new Properties();
                props.setProperty("micVolume", String.valueOf(this.micVolume));
                props.setProperty("speakerVolume", String.valueOf(this.speakerVolume));
                props.setProperty("activationThreshold", String.valueOf(this.activationThreshold));
                props.setProperty("inputMode", this.inputMode.name());
                props.setProperty("pttKeybindName", this.pttKeybindName);
                props.setProperty("muted", String.valueOf(this.muted));
                props.store(out, "Midnight Council Voice Settings");
            }
        } catch (IOException e) {
            System.err.println("Failed to save VoiceConfig: " + e.getMessage());
        }
    }

    public void validate() {
        this.micVolume = Math.max(0.0, Math.min(2.0, this.micVolume));
        this.speakerVolume = Math.max(0.0, Math.min(2.0, this.speakerVolume));
        this.activationThreshold = Math.max(-80.0, Math.min(0.0, this.activationThreshold));
        if (this.inputMode == null) {
            this.inputMode = InputMode.PTT;
        }
        if (this.pttKeybindName == null || this.pttKeybindName.trim().isEmpty()) {
            this.pttKeybindName = "V";
        }
    }

    public double getMicVolume() { return micVolume; }
    public void setMicVolume(double micVolume) { this.micVolume = micVolume; }

    public double getSpeakerVolume() { return speakerVolume; }
    public void setSpeakerVolume(double speakerVolume) { this.speakerVolume = speakerVolume; }

    public double getActivationThreshold() { return activationThreshold; }
    public void setActivationThreshold(double activationThreshold) { this.activationThreshold = activationThreshold; }

    public InputMode getInputMode() { return inputMode; }
    public void setInputMode(InputMode inputMode) { this.inputMode = inputMode; }

    public String getPttKeybindName() { return pttKeybindName; }
    public void setPttKeybindName(String pttKeybindName) { this.pttKeybindName = pttKeybindName; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
}
