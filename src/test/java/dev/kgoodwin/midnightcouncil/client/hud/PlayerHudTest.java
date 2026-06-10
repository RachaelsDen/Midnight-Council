package dev.kgoodwin.midnightcouncil.client.hud;

import dev.kgoodwin.midnightcouncil.client.MidnightCouncilClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHudTest {

    @BeforeEach
    void setUp() {
        MidnightCouncilClient.gameState().clear();
        SpeakingIndicator.getInstance().clear();
        SpeakingIndicator.getInstance().setLocalMuted(false);
    }

    @Test
    void testDependencies() {
        PlayerHud hud = new PlayerHud();
        assertTrue(hud instanceof net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement);
    }
}
