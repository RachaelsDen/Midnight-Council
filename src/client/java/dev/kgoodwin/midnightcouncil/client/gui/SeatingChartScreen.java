package dev.kgoodwin.midnightcouncil.client.gui;

import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.client.MidnightCouncilClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SeatingChartScreen extends Screen {

    public SeatingChartScreen() {
        super(Component.literal("Seating Chart"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        } else {
            super.onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        GameStateSnapshot snapshot = MidnightCouncilClient.getInstance().getCurrentGameState();
        
        if (snapshot == null) {
            String text = "Waiting for game state...";
            int textWidth = this.font.width(text);
            graphics.text(this.font, text, (this.width - textWidth) / 2, this.height / 2, 0xFFFFFFFF, true);
            return;
        }

        List<GameStateSnapshot.PlayerSnapshot> players = snapshot.players();
        int playerCount = players.size();

        if (playerCount > 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int radius = (int) (Math.min(this.width, this.height) * 0.35);

            for (int i = 0; i < playerCount; i++) {
                GameStateSnapshot.PlayerSnapshot player = players.get(i);
                int seatIndex = i;

                int[] pos = calculateSeatPosition(seatIndex, playerCount, centerX, centerY, radius);
                int px = pos[0];
                int py = pos[1];

                int boxWidth = 80;
                int boxHeight = 40;
                int boxX = px - boxWidth / 2;
                int boxY = py - boxHeight / 2;

                int bgColor = player.lifeState() == LifeState.ALIVE ? 0xFF00AA00 : 0xFF555555;
                graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bgColor);

                if (snapshot.nominatedSeat().isPresent() && snapshot.nominatedSeat().getAsInt() == player.seatNumber()) {
                    graphics.outline(boxX, boxY, boxWidth, boxHeight, 0xFFFF0000);
                }
                if (snapshot.markedSeat().isPresent() && snapshot.markedSeat().getAsInt() == player.seatNumber()) {
                    graphics.outline(boxX, boxY, boxWidth, boxHeight, 0xFFFF00FF);
                }

                String displayName = player.displayName();
                int textWidth = this.font.width(displayName);
                graphics.text(this.font, displayName, px - textWidth / 2, py, 0xFFFFFFFF, true);

                String seatNumText = "#" + player.seatNumber();
                int seatNumWidth = this.font.width(seatNumText);
                graphics.text(this.font, seatNumText, px - seatNumWidth / 2, py - 12, 0xFFFFFFFF, true);

                if (player.storyteller()) {
                    String stText = "★";
                    int stWidth = this.font.width(stText);
                    graphics.text(this.font, stText, px - stWidth / 2, py - 24, 0xFFFFAA00, true);
                }
            }
        }

        String title = snapshot.phase().name() + " (Day " + snapshot.dayCount() + ", Night " + snapshot.nightCount() + ")";
        int titleWidth = this.font.width(title);
        graphics.text(this.font, title, (this.width - titleWidth) / 2, 20, 0xFFFFFFFF, true);
    }

    static double calculateSeatAngle(int seatIndex, int playerCount) {
        if (playerCount <= 0) return 0;
        return 2 * Math.PI * seatIndex / playerCount - Math.PI / 2;
    }

    static int[] calculateSeatPosition(int seatIndex, int playerCount, int centerX, int centerY, int radius) {
        double angle = calculateSeatAngle(seatIndex, playerCount);
        int x = (int) Math.round(centerX + radius * Math.cos(angle));
        int y = (int) Math.round(centerY + radius * Math.sin(angle));
        return new int[]{x, y};
    }
}
