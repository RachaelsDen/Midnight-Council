package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.voice.VoiceConnection;
import dev.kgoodwin.midnightcouncil.voice.VoiceProximityRouter;
import dev.kgoodwin.midnightcouncil.voice.VoiceTransport;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FabricVoiceAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger("midnightcouncil");
	private static final int DEFAULT_VOICE_PORT = 24454;
	private static final double DEFAULT_VOICE_DISTANCE = 40.0;

	private final VoiceTransport voiceServer;
	private final Map<UUID, VoiceConnection> connections;
	private final int voicePort;
	private volatile MinecraftServer mcServer;

	public FabricVoiceAdapter() {
		this(DEFAULT_VOICE_PORT, DEFAULT_VOICE_DISTANCE);
	}

	public FabricVoiceAdapter(int voicePort, double voiceDistance) {
		this.voicePort = voicePort;
		VoiceProximityRouter router = new VoiceProximityRouter(voiceDistance);
		this.voiceServer = new VoiceTransport(router);
		this.connections = new ConcurrentHashMap<>();
	}

	public void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
		ServerPlayerEvents.AFTER_JOIN.register(this::onPlayerJoin);
		ServerPlayerEvents.LEAVE.register(this::onPlayerLeave);
	}

	public boolean isVoiceRunning() {
		return voiceServer.isRunning();
	}

	public int getConnectionCount() {
		return connections.size();
	}

	private void onServerStarted(MinecraftServer server) {
		this.mcServer = server;
		voiceServer.start(voicePort);
		LOGGER.info("Voice server started on UDP port {}", voicePort);
	}

	private void onServerStopping(MinecraftServer server) {
		for (VoiceConnection connection : connections.values()) {
			voiceServer.disconnect(connection);
		}
		connections.clear();
		voiceServer.stop();
		this.mcServer = null;
		LOGGER.info("Voice server stopped");
	}

	private void onEndServerTick(MinecraftServer server) {
		if (!voiceServer.isRunning()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			VoiceConnection connection = connections.get(player.getUUID());
			if (connection == null) {
				continue;
			}
			Vec3 pos = player.position();
			connection.setPosition(new Position(pos.x, pos.y, pos.z));
		}
	}

	private void onPlayerJoin(ServerPlayer player) {
		if (!voiceServer.isRunning()) {
			return;
		}

		UUID playerId = player.getUUID();
		if (connections.containsKey(playerId)) {
			return;
		}

		VoiceConnection connection = new VoiceConnection(PlayerReference.ofUuid(playerId));
		Vec3 pos = player.position();
		connection.setPosition(new Position(pos.x, pos.y, pos.z));

		connections.put(playerId, connection);
		voiceServer.connect(connection);
		LOGGER.debug("Voice connection registered for player {}", playerId);
	}

	private void onPlayerLeave(ServerPlayer player) {
		VoiceConnection connection = connections.remove(player.getUUID());
		if (connection != null) {
			voiceServer.disconnect(connection);
			LOGGER.debug("Voice connection unregistered for player {}", player.getUUID());
		}
	}
}
