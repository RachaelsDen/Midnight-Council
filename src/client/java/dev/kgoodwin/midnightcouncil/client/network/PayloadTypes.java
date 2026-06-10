package dev.kgoodwin.midnightcouncil.client.network;

import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.PlayerSnapshot;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

public final class PayloadTypes {

	private static final String MOD_ID = "midnightcouncil";

	private PayloadTypes() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(GameStateSyncPayload.ID, GameStateSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VoiceHandshakePayload.ID, VoiceHandshakePayload.CODEC);
	}

	public record GameStateSyncPayload(GameStateSnapshot snapshot) implements CustomPacketPayload {

		public static final Type<GameStateSyncPayload> ID =
				new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "game_state_sync"));

		public static final StreamCodec<RegistryFriendlyByteBuf, GameStateSyncPayload> CODEC =
				StreamCodec.of(GameStateSyncPayload::write, GameStateSyncPayload::read);

		public GameStateSyncPayload {
			Objects.requireNonNull(snapshot, "snapshot");
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}

		private static void write(RegistryFriendlyByteBuf buffer, GameStateSyncPayload payload) {
			GameStateSnapshot snapshot = payload.snapshot();
			buffer.writeEnum(snapshot.phase());
			buffer.writeVarInt(snapshot.dayCount());
			buffer.writeVarInt(snapshot.nightCount());
			writeNullableSeat(buffer, snapshot.nominatedSeat());
			writeNullableSeat(buffer, snapshot.markedSeat());
			buffer.writeBoolean(snapshot.timerActive());
			buffer.writeVarInt(snapshot.players().size());
			for (PlayerSnapshot player : snapshot.players()) {
				writePlayerSnapshot(buffer, player);
			}
		}

		private static GameStateSyncPayload read(RegistryFriendlyByteBuf buffer) {
			dev.kgoodwin.midnightcouncil.api.GamePhase phase = buffer.readEnum(dev.kgoodwin.midnightcouncil.api.GamePhase.class);
			int dayCount = buffer.readVarInt();
			int nightCount = buffer.readVarInt();
			Integer nominatedSeat = readNullableSeat(buffer);
			Integer markedSeat = readNullableSeat(buffer);
			boolean timerActive = buffer.readBoolean();
			int playerCount = buffer.readVarInt();
			java.util.ArrayList<PlayerSnapshot> players = new java.util.ArrayList<>(playerCount);
			for (int index = 0; index < playerCount; index++) {
				players.add(readPlayerSnapshot(buffer));
			}

			GameStateSnapshot snapshot = new GameStateSnapshot(
					phase,
					dayCount,
					nightCount,
					nominatedSeat,
					markedSeat,
					timerActive,
					List.copyOf(players));
			return new GameStateSyncPayload(snapshot);
		}
	}

	public record VoiceHandshakePayload(String host, int port, byte[] connectToken) implements CustomPacketPayload {

		public static final Type<VoiceHandshakePayload> ID =
				new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "voice_handshake"));

		public static final StreamCodec<RegistryFriendlyByteBuf, VoiceHandshakePayload> CODEC =
				StreamCodec.of(VoiceHandshakePayload::write, VoiceHandshakePayload::read);

		public VoiceHandshakePayload {
			String validatedHost = Objects.requireNonNull(host, "host");
			if (validatedHost.isBlank()) {
				throw new IllegalArgumentException("host cannot be blank");
			}
			if (port <= 0 || port > 65535) {
				throw new IllegalArgumentException("port must be between 1 and 65535");
			}

			connectToken = Objects.requireNonNull(connectToken, "connectToken").clone();
		}

		@Override
		public byte[] connectToken() {
			return this.connectToken.clone();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}

		private static void write(RegistryFriendlyByteBuf buffer, VoiceHandshakePayload payload) {
			buffer.writeUtf(payload.host());
			buffer.writeVarInt(payload.port());
			buffer.writeByteArray(payload.connectToken());
		}

		private static VoiceHandshakePayload read(RegistryFriendlyByteBuf buffer) {
			return new VoiceHandshakePayload(
					buffer.readUtf(),
					buffer.readVarInt(),
					buffer.readByteArray());
		}
	}

	private static void writeNullableSeat(RegistryFriendlyByteBuf buffer, Integer seat) {
		buffer.writeBoolean(seat != null);
		if (seat != null) {
			buffer.writeVarInt(seat);
		}
	}

	private static Integer readNullableSeat(RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readVarInt() : null;
	}

	private static void writePlayerSnapshot(RegistryFriendlyByteBuf buffer, PlayerSnapshot snapshot) {
		buffer.writeUtf(snapshot.name());
		buffer.writeVarInt(snapshot.seatIndex());
		buffer.writeEnum(snapshot.lifeState());
		buffer.writeEnum(snapshot.sleepState());
	}

	private static PlayerSnapshot readPlayerSnapshot(RegistryFriendlyByteBuf buffer) {
		return new PlayerSnapshot(
				buffer.readUtf(),
				buffer.readVarInt(),
				buffer.readEnum(dev.kgoodwin.midnightcouncil.api.game.LifeState.class),
				buffer.readEnum(dev.kgoodwin.midnightcouncil.api.game.SleepState.class));
	}
}
