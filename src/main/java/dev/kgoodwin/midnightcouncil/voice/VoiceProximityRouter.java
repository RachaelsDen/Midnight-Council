package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class VoiceProximityRouter implements VoiceRoutingStrategy {

	private final double maxHearingDistance;

	public VoiceProximityRouter(double maxHearingDistance) {
		if (maxHearingDistance <= 0) {
			throw new IllegalArgumentException("maxHearingDistance must be positive");
		}
		this.maxHearingDistance = maxHearingDistance;
	}

	public VoiceProximityRouter() {
		this(32.0);
	}

	@Override
	public Collection<VoiceClientConnection> route(VoiceServer server, AudioPacket packet, GameState state) {
		PlayerReference senderId = packet.senderId();

		Optional<VoiceClientConnection> senderConn = server.getConnections().stream()
				.filter(c -> c.getPlayerId().equals(senderId))
				.findFirst();

		if (senderConn.isEmpty()) {
			return List.of();
		}

		VoiceClientConnection sender = senderConn.get();
		MicrophoneState micState = sender.getMicrophoneState();
		if (micState != MicrophoneState.ACTIVE && micState != MicrophoneState.PUSH_TO_TALK) {
			return List.of();
		}

		Optional<PlayerEntry> senderEntry = state.getPlayers().getByPlayerReference(senderId);
		if (senderEntry.isPresent() && senderEntry.get().getLifeState() == LifeState.DEAD) {
			return List.of();
		}

		if (senderEntry.isPresent() && state.getPhase() == GamePhase.NIGHT && senderEntry.get().isSleeping()) {
			return List.of();
		}

		Position senderPos = sender.getPosition();
		Collection<VoiceClientConnection> recipients = new ArrayList<>();

		for (VoiceClientConnection candidate : server.getConnections()) {
			if (candidate.getPlayerId().equals(senderId)) {
				continue;
			}

			if (!isWithinDistance(senderPos, candidate.getPosition())) {
				continue;
			}

			if (!canHear(candidate, state)) {
				continue;
			}

			recipients.add(candidate);
		}

		return recipients;
	}

	private boolean canHear(VoiceClientConnection connection, GameState state) {
		GamePhase phase = state.getPhase();
		PlayerReference playerId = connection.getPlayerId();

		Optional<PlayerEntry> entry = state.getPlayers().getByPlayerReference(playerId);

		if (entry.isEmpty()) {
			return true;
		}

		PlayerEntry player = entry.get();

		if (phase == GamePhase.NIGHT) {
			if (player.getLifeState() == LifeState.DEAD) {
				return false;
			}
			if (player.isSleeping()) {
				return false;
			}
			return true;
		}

		if (phase == GamePhase.DAY || phase == GamePhase.NOMINATION
				|| phase == GamePhase.VOTING || phase == GamePhase.EXECUTION) {
			if (player.getLifeState() == LifeState.DEAD) {
				return true;
			}
			return true;
		}

		return true;
	}

	private boolean isWithinDistance(Position a, Position b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		double dz = a.z() - b.z();
		double distanceSquared = dx * dx + dy * dy + dz * dz;
		return distanceSquared <= maxHearingDistance * maxHearingDistance;
	}
}
