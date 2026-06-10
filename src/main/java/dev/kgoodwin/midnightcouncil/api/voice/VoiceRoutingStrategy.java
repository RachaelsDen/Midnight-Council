package dev.kgoodwin.midnightcouncil.api.voice;

import dev.kgoodwin.midnightcouncil.api.game.GameState;

import java.util.Collection;

public interface VoiceRoutingStrategy {

	Collection<VoiceClientConnection> route(VoiceServer server, AudioPacket packet, GameState state);
}
