package dev.kgoodwin.midnightcouncil.client;

import dev.kgoodwin.midnightcouncil.client.network.ClientNetworkHandler;
import dev.kgoodwin.midnightcouncil.client.network.PayloadTypes;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;

import net.fabricmc.api.ClientModInitializer;

public final class MidnightCouncilClient implements ClientModInitializer {

	private static final ClientGameState GAME_STATE = new ClientGameState();
	private static ClientNetworkHandler networkHandler;

	public static ClientGameState gameState() {
		return GAME_STATE;
	}

	@Override
	public void onInitializeClient() {
		PayloadTypes.register();
		networkHandler = new ClientNetworkHandler(GAME_STATE);
		networkHandler.registerReceivers();
	}
}
