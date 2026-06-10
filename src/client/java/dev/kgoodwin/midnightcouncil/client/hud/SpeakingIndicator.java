package dev.kgoodwin.midnightcouncil.client.hud;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpeakingIndicator {

    private static final SpeakingIndicator INSTANCE = new SpeakingIndicator();

    private final Set<String> speakingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean localMuted = false;

    private SpeakingIndicator() {
    }

    public static SpeakingIndicator getInstance() {
        return INSTANCE;
    }

    public void setLocalMuted(boolean muted) {
        this.localMuted = muted;
    }

    public boolean isLocalMuted() {
        return localMuted;
    }

    public void setSpeaking(String playerName, boolean speaking) {
        if (speaking) {
            speakingPlayers.add(playerName);
        } else {
            speakingPlayers.remove(playerName);
        }
    }

    public boolean isSpeaking(String playerName) {
        return speakingPlayers.contains(playerName);
    }

    public void clear() {
        speakingPlayers.clear();
    }
}
