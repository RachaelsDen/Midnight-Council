package dev.kgoodwin.midnightcouncil.client.network;

import dev.kgoodwin.midnightcouncil.client.state.ClientGameState;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.VoiceHandshakeState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Objects;

public final class ClientNetworkHandler {

	private final ClientGameState state;

	public ClientNetworkHandler(ClientGameState state) {
		this.state = Objects.requireNonNull(state, "state");
	}

	public void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.GameStateSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> this.state.update(payload.snapshot())));

		ClientPlayNetworking.registerGlobalReceiver(PayloadTypes.VoiceHandshakePayload.ID, (payload, context) ->
				context.client().execute(() -> this.state.updateVoiceHandshake(
						new VoiceHandshakeState(payload.host(), payload.port(), payload.connectToken()))));
	}
}
