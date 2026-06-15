package dev.kgoodwin.midnightcouncil.client.gui;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot.PlayerSnapshot;
import dev.kgoodwin.midnightcouncil.client.MidnightCouncilClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public class GameHudOverlay implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        GameStateSnapshot snapshot = MidnightCouncilClient.getInstance().getCurrentGameState();
        if (snapshot == null || snapshot.phase() == GamePhase.IDLE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int bgWidth = 200;
        int bgHeight = snapshot.timerActive() ? 60 : 50;
        int x = (screenWidth - bgWidth) / 2;
        int y = 5;

        graphics.fill(x, y, x + bgWidth, y + bgHeight, 0x80000000);

        String phaseText = snapshot.phase().name();
        int phaseColor = getPhaseColor(snapshot.phase());
        int phaseX = x + (bgWidth - font.width(phaseText)) / 2;
        graphics.text(font, phaseText, phaseX, y + 5, phaseColor, true);

        String countersText = formatPhaseDisplay(snapshot.phase(), snapshot.dayCount(), snapshot.nightCount());
        int countersX = x + (bgWidth - font.width(countersText)) / 2;
        graphics.text(font, countersText, countersX, y + 17, 0xFFFFFFFF, true);

        int aliveCount = formatPlayerCountText(snapshot.players());
        int totalCount = snapshot.players().size();
        String playersText = "Players: " + aliveCount + " alive / " + totalCount + " total";
        int playersX = x + (bgWidth - font.width(playersText)) / 2;
        graphics.text(font, playersText, playersX, y + 29, 0xFFFFFFFF, true);

        if (snapshot.timerActive()) {
            String timerText = "Timer Active";
            int timerX = x + (bgWidth - font.width(timerText)) / 2;
            graphics.text(font, timerText, timerX, y + 41, 0xFFFF8800, true);
        }
    }

    static int getPhaseColor(GamePhase phase) {
        if (phase == GamePhase.DAY) {
            return 0xFFFFAA00;
        } else if (phase == GamePhase.NIGHT) {
            return 0xFF5555FF;
        } else {
            return 0xFFFFFFFF;
        }
    }

    static String formatPhaseDisplay(GamePhase phase, int dayCount, int nightCount) {
        return "Day: " + dayCount + " | Night: " + nightCount;
    }

    static int formatPlayerCountText(List<PlayerSnapshot> players) {
        return (int) players.stream().filter(p -> p.lifeState() == LifeState.ALIVE).count();
    }
}
