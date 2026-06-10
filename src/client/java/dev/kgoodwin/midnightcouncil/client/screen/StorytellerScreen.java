package dev.kgoodwin.midnightcouncil.client.screen;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StorytellerScreen extends Screen {

    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 80;
    private static final int BUTTON_WIDTH = 120;
    private static final int SMALL_BUTTON_WIDTH = 80;
    private static final int EDITBOX_WIDTH = 40;
    private static final long COMMAND_COOLDOWN_MS = 250;

    private ClientGameState state;
    private long commandSentTime = 0;
    private String lastCommandSent = "";
    private HeaderAndFooterLayout layout;
    private SeatRosterWidget seatRoster;
    private LinearLayout footerLayout;
    private EditBox nominateTargetEditBox;

    public StorytellerScreen(ClientGameState state) {
        super(Component.literal("Storyteller Tools"));
        this.state = state;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

        layout.addTitleHeader(getTitle(), font);
        
        ClientGameState.GameStateSnapshot snapshot = state.current().orElse(null);
        String phaseName = snapshot != null ? snapshot.phase().name() : "WAITING";
        layout.addToHeader(new StringWidget(Component.literal("Phase: " + phaseName), font));

        seatRoster = new SeatRosterWidget(10, HEADER_HEIGHT);
        if (snapshot != null) {
            seatRoster.updateSeats(snapshot.players());
            for (GuiEventListener widget : seatRoster.getWidgets()) {
                if (widget instanceof net.minecraft.client.gui.components.AbstractWidget rosterWidget) {
                    addRenderableWidget(rosterWidget);
                }
            }
        }

        footerLayout = layout.addToFooter(LinearLayout.horizontal().spacing(4));
        buildPhaseFooter();

        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    protected void sendCommand(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
            commandSentTime = System.currentTimeMillis();
            lastCommandSent = command;
        }
    }

    private boolean isOnCooldown() {
        return System.currentTimeMillis() - commandSentTime < COMMAND_COOLDOWN_MS;
    }

    private Button.OnPress withCooldown(Button.OnPress action) {
        return button -> {
            if (!isOnCooldown()) {
                action.onPress(button);
            }
        };
    }

    public void refreshState(ClientGameState newState) {
        this.state = newState;
        rebuildWidgets();
    }

    private void buildPhaseFooter() {
        ClientGameState.GameStateSnapshot snapshot = state.current().orElse(null);
        GamePhase phase = snapshot != null ? snapshot.phase() : null;
        
        PhasePanelSelector.PanelType panelType = PhasePanelSelector.selectPanel(phase);
        
        switch (panelType) {
            case IDLE -> buildIdlePanel(footerLayout);
            case SETUP -> buildSetupPanel(footerLayout);
            case SEATING -> buildSeatingPanel(footerLayout);
            case DAY -> buildDayPanel(footerLayout);
            case NOMINATION -> buildNominationPanel(footerLayout);
            case VOTING -> buildVotingPanel(footerLayout);
            case EXECUTION -> buildExecutionPanel(footerLayout);
            case NIGHT -> buildNightPanel(footerLayout);
            case GAME_OVER -> buildGameOverPanel(footerLayout);
            default -> footerLayout.addChild(new StringWidget(Component.literal("Phase not implemented"), font));
        }
    }

    private void buildIdlePanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Setup Game"), withCooldown(button -> {
            sendCommand("midnight setup");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildSetupPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Start Seating"), withCooldown(button -> {
            sendCommand("midnight start");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildSeatingPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Start Game"), withCooldown(button -> {
            sendCommand("midnight start");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildDayPanel(LinearLayout footer) {
        LinearLayout column = LinearLayout.vertical().spacing(4);

        LinearLayout nomRow = LinearLayout.horizontal().spacing(4);
        if (nominateTargetEditBox == null) {
            nominateTargetEditBox = new EditBox(font, 0, 0, EDITBOX_WIDTH, 20, Component.literal("Seat"));
            nominateTargetEditBox.setMaxLength(3);
        }
        nomRow.addChild(new StringWidget(Component.literal("Nominator Seat: "), font));
        nomRow.addChild(nominateTargetEditBox);

        nomRow.addChild(Button.builder(Component.literal("Nominate"), withCooldown(button -> {
            String value = nominateTargetEditBox.getValue().trim();
            if (!value.isEmpty()) {
                sendCommand("midnight nominate " + value);
            }
        })).width(BUTTON_WIDTH).build());

        nomRow.addChild(Button.builder(Component.literal("Go to Night"), withCooldown(button -> {
            sendCommand("midnight phase next");
        })).width(BUTTON_WIDTH).build());

        column.addChild(nomRow);
        footer.addChild(column);
    }

    private void buildNominationPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Start Vote"), withCooldown(button -> {
            sendCommand("midnight phase next");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildVotingPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("End Vote"), withCooldown(button -> {
            sendCommand("midnight phase next");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildExecutionPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Execute"), withCooldown(button -> {
            ClientGameState.GameStateSnapshot snapshot = state.current().orElse(null);
            if (snapshot != null && snapshot.markedSeat() != null) {
                sendCommand("midnight execute " + snapshot.markedSeat());
            }
        })).width(BUTTON_WIDTH).build());
        
        footer.addChild(Button.builder(Component.literal("Spare"), withCooldown(button -> {
            sendCommand("midnight spare");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildNightPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("Wake All (Start Day)"), withCooldown(button -> {
            sendCommand("midnight phase next");
        })).width(BUTTON_WIDTH).build());
    }

    private void buildGameOverPanel(LinearLayout footer) {
        footer.addChild(Button.builder(Component.literal("End Game"), withCooldown(button -> {
            sendCommand("midnight end");
        })).width(BUTTON_WIDTH).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        long elapsed = System.currentTimeMillis() - commandSentTime;
        if (elapsed < 2000 && !lastCommandSent.isEmpty()) {
            graphics.centeredText(font, Component.literal("Sent: " + lastCommandSent),
                    width / 2, HEADER_HEIGHT + 5, 0xAAAAAA);
        }
    }
}
