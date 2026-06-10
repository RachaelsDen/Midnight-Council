package dev.kgoodwin.midnightcouncil.client.screen;

import dev.kgoodwin.midnightcouncil.client.config.VoiceConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class VoiceSettingsScreen extends Screen {

    private final Screen parent;
    private VoiceConfig config;

    private ConfigSlider micVolumeSlider;
    private ConfigSlider speakerVolumeSlider;
    private ConfigSlider thresholdSlider;
    private Button inputModeButton;
    private Button muteButton;
    private Button testMicButton;
    private Button doneButton;

    public VoiceSettingsScreen(Screen parent) {
        super(Component.literal("Voice Settings"));
        this.parent = parent;
        this.config = VoiceConfig.getInstance();
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 6;
        int width = 200;
        int height = 20;
        int spacing = 24;

        this.micVolumeSlider = new ConfigSlider(
            x, y, width, height, Component.literal("Microphone Volume"),
            0.0, 2.0, config.getMicVolume(), false,
            val -> config.setMicVolume(val)
        );
        addRenderableWidget(micVolumeSlider);
        y += spacing;

        this.speakerVolumeSlider = new ConfigSlider(
            x, y, width, height, Component.literal("Speaker Volume"),
            0.0, 2.0, config.getSpeakerVolume(), false,
            val -> config.setSpeakerVolume(val)
        );
        addRenderableWidget(speakerVolumeSlider);
        y += spacing;

        this.thresholdSlider = new ConfigSlider(
            x, y, width, height, Component.literal("Voice Activation Threshold"),
            -80.0, 0.0, config.getActivationThreshold(), true,
            val -> config.setActivationThreshold(val)
        );
        addRenderableWidget(thresholdSlider);
        y += spacing;

        this.inputModeButton = Button.builder(getInputModeText(), button -> {
            if (config.getInputMode() == VoiceConfig.InputMode.PTT) {
                config.setInputMode(VoiceConfig.InputMode.VOICE_ACTIVATION);
            } else {
                config.setInputMode(VoiceConfig.InputMode.PTT);
            }
            button.setMessage(getInputModeText());
        }).bounds(x, y, width, height).build();
        addRenderableWidget(inputModeButton);
        y += spacing;

        this.muteButton = Button.builder(getMuteText(), button -> {
            config.setMuted(!config.isMuted());
            button.setMessage(getMuteText());
        }).bounds(x, y, width, height).build();
        addRenderableWidget(muteButton);
        y += spacing;

        this.testMicButton = Button.builder(Component.literal("Microphone test requires in-game"), button -> {
        }).bounds(x, y, width, height).build();
        this.testMicButton.active = false;
        addRenderableWidget(testMicButton);
        y += spacing;

        this.doneButton = Button.builder(Component.literal("Done"), button -> {
            this.onClose();
        }).bounds(x, y, width, height).build();
        addRenderableWidget(doneButton);
    }

    private Component getInputModeText() {
        return Component.literal("Input Mode: " + config.getInputMode().name());
    }

    private Component getMuteText() {
        return Component.literal(config.isMuted() ? "Muted: Yes" : "Muted: No");
    }

    @Override
    public void onClose() {
        config.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class ConfigSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final boolean isInt;
        private final Consumer<Double> onChange;
        private final Component label;

        public ConfigSlider(int x, int y, int width, int height, Component label, double min, double max, double initialValue, boolean isInt, Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), (Math.max(min, Math.min(max, initialValue)) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.isInt = isInt;
            this.onChange = onChange;
            this.label = label;
            this.updateMessage();
        }

        public double getSliderValue() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            double val = getSliderValue();
            if (isInt) {
                setMessage(Component.literal(label.getString() + ": " + (int) Math.round(val)));
            } else {
                setMessage(Component.literal(label.getString() + ": " + String.format("%.2f", val)));
            }
        }

        @Override
        protected void applyValue() {
            onChange.accept(getSliderValue());
        }
    }
}
