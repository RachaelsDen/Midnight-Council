package dev.kgoodwin.midnightcouncil.client.hud;

import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.client.MidnightCouncilClient;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class PlayerHud implements HudElement {

    private static final int COLOR_PHASE = 0xFFFFFFFF;
    private static final int COLOR_ALIVE = 0xFF00AA00;
    private static final int COLOR_DEAD = 0xFFAAAAAA;
    private static final int COLOR_AWAKE = 0xFFFFFF00;
    private static final int COLOR_ASLEEP = 0xFF5555FF;
    private static final int COLOR_SPEAKER = 0xFF00CC00;
    private static final int COLOR_MUTED = 0xFFFF5555;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        int screenHeight = client.getWindow().getGuiScaledHeight();
        int y = screenHeight - 60;
        int x = 10;

        ClientGameState state = MidnightCouncilClient.gameState();
        Optional<ClientGameState.GameStateSnapshot> currentOpt = state.current();

        if (currentOpt.isEmpty()) {
            return;
        }

        ClientGameState.GameStateSnapshot current = currentOpt.get();

        graphics.text(client.font, Component.literal("Phase: " + current.phase().name()), x, y, COLOR_PHASE);
        y += 10;

        Optional<String> localPlayerNameOpt = state.localPlayerName();
        if (localPlayerNameOpt.isPresent()) {
            String localPlayerName = localPlayerNameOpt.get();
            Optional<ClientGameState.PlayerSnapshot> localPlayerOpt = current.players().stream()
                    .filter(p -> p.name().equals(localPlayerName))
                    .findFirst();

            if (localPlayerOpt.isPresent()) {
                ClientGameState.PlayerSnapshot localPlayer = localPlayerOpt.get();

                Component lifeText = Component.literal("Status: " + localPlayer.lifeState().name());
                int lifeColor = localPlayer.lifeState() == LifeState.ALIVE ? COLOR_ALIVE : COLOR_DEAD;
                graphics.text(client.font, lifeText, x, y, lifeColor);
                y += 10;

                Component sleepText = Component.literal("State: " + localPlayer.sleepState().name());
                int sleepColor = localPlayer.sleepState() == SleepState.AWAKE ? COLOR_AWAKE : COLOR_ASLEEP;
                graphics.text(client.font, sleepText, x, y, sleepColor);
                y += 10;
            }
        }

        SpeakingIndicator indicator = SpeakingIndicator.getInstance();
        
        if (indicator.isLocalMuted()) {
            graphics.text(client.font, Component.literal("MUTED"), x, y, COLOR_MUTED);
            y += 10;
        }

        for (ClientGameState.PlayerSnapshot player : current.players()) {
            if (indicator.isSpeaking(player.name())) {
                graphics.text(client.font, Component.literal(player.name() + " \uD83D\uDD0A"), x, y, COLOR_SPEAKER);
                y += 10;
            }
        }
    }
}
