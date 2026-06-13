package dev.kgoodwin.midnightcouncil.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MidnightCouncilModTest {

    @TempDir
    Path tempDir;

    private MidnightCouncilMod mod;
    private FabricNetworkAdapter networkAdapter;

    @BeforeEach
    void setUp() {
        mod = new MidnightCouncilMod();
        mod.setConfigDirOverride(tempDir);
        MinecraftServer server = mock(MinecraftServer.class);
        networkAdapter = new FabricNetworkAdapter(server);
    }

    @Test
    void dispatchServerboundPayloadRoutesUuidAndPayloadToRegisteredHandler() {
        UUID playerUuid = UUID.randomUUID();
        byte[] bytes = new byte[] {4, 2};
        MidnightCouncilPayload payload = new MidnightCouncilPayload("vote", bytes);
        AtomicReference<PlayerReference> capturedPlayer = new AtomicReference<>();

        networkAdapter.registerReceiver("vote", (playerReference, channel, inboundBytes) -> {
            capturedPlayer.set(playerReference);
            assertEquals("vote", channel);
            assertArrayEquals(bytes, inboundBytes);
        });

        MidnightCouncilMod.dispatchServerboundPayload(networkAdapter, playerUuid, payload);

        assertNotNull(capturedPlayer.get());
        assertEquals(PlayerReference.from(playerUuid), capturedPlayer.get());
    }

    @Test
    void rewiresAdaptersWhenNewServerInstanceAppears() {
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);

        mod.onServerTick(serverA);
        Object configA = mod.configAdapter();
        Object worldA = mod.worldAdapter();
        Object networkA = mod.networkAdapter();
        Object permissionA = mod.permissionAdapter();
        Object schedulerA = mod.schedulerAdapter();
        Object loggerA = mod.loggerAdapter();

        mod.onServerTick(serverB);

        assertNotSame(configA, mod.configAdapter());
        assertNotSame(worldA, mod.worldAdapter());
        assertNotSame(networkA, mod.networkAdapter());
        assertNotSame(permissionA, mod.permissionAdapter());
        assertNotSame(schedulerA, mod.schedulerAdapter());
        assertNotSame(loggerA, mod.loggerAdapter());
    }

    @Test
    void doesNotRewireForSameServerInstance() {
        MinecraftServer server = mock(MinecraftServer.class);

        mod.onServerTick(server);
        Object config = mod.configAdapter();
        Object world = mod.worldAdapter();
        Object network = mod.networkAdapter();
        Object permission = mod.permissionAdapter();
        Object scheduler = mod.schedulerAdapter();
        Object logger = mod.loggerAdapter();

        mod.onServerTick(server);

        assertSame(config, mod.configAdapter());
        assertSame(world, mod.worldAdapter());
        assertSame(network, mod.networkAdapter());
        assertSame(permission, mod.permissionAdapter());
        assertSame(scheduler, mod.schedulerAdapter());
        assertSame(logger, mod.loggerAdapter());
    }

    @Test
    void schedulerTasksDoNotLeakAcrossServerRewire() {
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);
        AtomicInteger runs = new AtomicInteger();

        mod.onServerTick(serverA);
        mod.schedulerAdapter().runNextTick(runs::incrementAndGet);

        mod.onServerTick(serverB);
        assertEquals(0, runs.get());

        mod.schedulerAdapter().runNextTick(runs::incrementAndGet);
        mod.schedulerAdapter().tick();
        assertEquals(1, runs.get());
    }

    @Test
    void clearsAdaptersWhenActiveServerStops() {
        MinecraftServer server = mock(MinecraftServer.class);

        mod.onServerTick(server);
        mod.onServerStopped(server);

        assertNull(mod.configAdapter());
        assertNull(mod.worldAdapter());
        assertNull(mod.networkAdapter());
        assertNull(mod.permissionAdapter());
        assertNull(mod.schedulerAdapter());
        assertNull(mod.loggerAdapter());
        assertNull(mod.voiceAdapter());
        assertNull(mod.gameSession());
    }

    @Test
    void serverStartedInitializesAndStartsVoiceAdapter() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);

        mod.onServerStarted(server);

        assertNotNull(mod.voiceAdapter());
        assertNotNull(mod.gameSession());
        assertTrue(mod.voiceAdapter().isVoiceRunning());
        PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());
        FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(
                mod.voiceAdapter().createConnectHandoff(playerReference));
        assertTrue(handoff.port() > 0);
        assertEquals(playerReference.value(), handoff.playerId());
        assertEquals(Long.BYTES + 32, handoff.token().length);

        mod.onServerStopped(server);
        assertNull(mod.voiceAdapter());
        assertNull(mod.gameSession());
    }
}
