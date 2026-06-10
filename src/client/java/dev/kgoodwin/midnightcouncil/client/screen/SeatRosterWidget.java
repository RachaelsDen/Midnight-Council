package dev.kgoodwin.midnightcouncil.client.screen;

import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SeatRosterWidget {

	private static final int ROW_HEIGHT = 24;
	private static final int SEAT_LABEL_WIDTH = 40;
	private static final int PLAYER_NAME_WIDTH = 100;
	private static final int STATUS_WIDTH = 48;
	static final int ACTION_BUTTON_WIDTH = 48;
	static final int ACTION_BUTTON_HEIGHT = 18;
	private static final int BUTTON_GAP = 4;

	private final int x;
	private final int y;

	private final List<SeatRow> rows = new ArrayList<>();
	private final List<GuiEventListener> allWidgets = new ArrayList<>();

	public SeatRosterWidget(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void updateSeats(List<ClientGameState.PlayerSnapshot> seats) {
		rows.clear();
		allWidgets.clear();

		int rowY = y;
		for (ClientGameState.PlayerSnapshot seat : seats) {
			SeatRow row = new SeatRow(seat, x, rowY);
			rows.add(row);
			allWidgets.addAll(row.widgets);
			rowY += ROW_HEIGHT;
		}
	}

	public List<GuiEventListener> getWidgets() {
		return List.copyOf(allWidgets);
	}

	public void reposition(int newX, int newY) {
		int rowY = newY;
		for (SeatRow row : rows) {
			row.reposition(newX, rowY);
			rowY += ROW_HEIGHT;
		}
	}

	public int getHeight() {
		return rows.size() * ROW_HEIGHT;
	}

	public int getRowCount() {
		return rows.size();
	}

	private static void sendCommand(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
	}

	public class SeatRow {
		final ClientGameState.PlayerSnapshot seat;
		final List<GuiEventListener> widgets = new ArrayList<>();

		SeatRow(ClientGameState.PlayerSnapshot seat, int startX, int startY) {
			this.seat = seat;

			Minecraft mc = Minecraft.getInstance();
			var font = mc.font;

			StringWidget seatLabel = new StringWidget(
					Component.literal("Seat " + seat.seatIndex()), font);
			seatLabel.setPosition(startX + 4, startY + (ROW_HEIGHT - 9) / 2);
			seatLabel.setWidth(SEAT_LABEL_WIDTH);
			widgets.add(seatLabel);

			StringWidget nameLabel = new StringWidget(
					Component.literal(seat.name()), font);
			nameLabel.setPosition(startX + SEAT_LABEL_WIDTH, startY + (ROW_HEIGHT - 9) / 2);
			nameLabel.setWidth(PLAYER_NAME_WIDTH);
			widgets.add(nameLabel);

			String statusText;
			int statusColor;
			if (seat.lifeState() == LifeState.DEAD) {
				statusText = "DEAD";
				statusColor = 0xFF5555;
			} else if (seat.sleepState() == SleepState.SLEEPING) {
				statusText = "ASLEEP";
				statusColor = 0x5555FF;
			} else {
				statusText = "AWAKE";
				statusColor = 0x55FF55;
			}
			StringWidget statusLabel = new StringWidget(
					Component.literal(statusText).withStyle(s ->
							s.withColor(statusColor)), font);
			int statusX = startX + SEAT_LABEL_WIDTH + PLAYER_NAME_WIDTH;
			statusLabel.setPosition(statusX, startY + (ROW_HEIGHT - 9) / 2);
			statusLabel.setWidth(STATUS_WIDTH);
			widgets.add(statusLabel);

			int btnX = statusX + STATUS_WIDTH + BUTTON_GAP;
			String targetName = seat.name();

			if (seat.lifeState() == LifeState.ALIVE && seat.sleepState() == SleepState.AWAKE) {
				widgets.add(Button.builder(
						Component.literal("Kill"),
						btn -> sendCommand("midnight kill " + targetName)
				).bounds(btnX, startY + (ROW_HEIGHT - ACTION_BUTTON_HEIGHT) / 2,
						ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
				btnX += ACTION_BUTTON_WIDTH + BUTTON_GAP;

				widgets.add(Button.builder(
						Component.literal("Sleep"),
						btn -> sendCommand("midnight sleep " + targetName)
				).bounds(btnX, startY + (ROW_HEIGHT - ACTION_BUTTON_HEIGHT) / 2,
						ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());

			} else if (seat.lifeState() == LifeState.DEAD) {
				widgets.add(Button.builder(
						Component.literal("Revive"),
						btn -> sendCommand("midnight revive " + targetName)
				).bounds(btnX, startY + (ROW_HEIGHT - ACTION_BUTTON_HEIGHT) / 2,
						ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());

			} else if (seat.sleepState() == SleepState.SLEEPING) {
				widgets.add(Button.builder(
						Component.literal("Wake"),
						btn -> sendCommand("midnight wake " + targetName)
				).bounds(btnX, startY + (ROW_HEIGHT - ACTION_BUTTON_HEIGHT) / 2,
						ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
				btnX += ACTION_BUTTON_WIDTH + BUTTON_GAP;

				widgets.add(Button.builder(
						Component.literal("Kill"),
						btn -> sendCommand("midnight kill " + targetName)
				).bounds(btnX, startY + (ROW_HEIGHT - ACTION_BUTTON_HEIGHT) / 2,
						ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
			}
		}

		void reposition(int newX, int newY) {
			int labelX = newX;
			for (GuiEventListener widget : widgets) {
				if (widget instanceof StringWidget sw) {
					sw.setX(labelX);
					sw.setY(newY + (ROW_HEIGHT - 9) / 2);
					labelX += sw.getWidth();
				}
			}
		}
	}
}
