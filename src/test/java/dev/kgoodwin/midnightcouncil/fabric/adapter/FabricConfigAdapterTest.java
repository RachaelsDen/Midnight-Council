package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricConfigAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void loadClearsRemovedEntriesBeforeReloading() throws IOException {
        Path configFile = tempDir.resolve("midnightcouncil.properties");
        Files.writeString(configFile, "discussionTimerSeconds=90\nnominationTimerSeconds=45\n");

        FabricConfigAdapter adapter = new FabricConfigAdapter(tempDir, "midnightcouncil.properties");
        adapter.load();

        assertEquals(Optional.of(90), adapter.get("discussionTimerSeconds", Integer.class));
        assertEquals(Optional.of(45), adapter.get("nominationTimerSeconds", Integer.class));

        Files.writeString(configFile, "discussionTimerSeconds=120\n");
        adapter.load();

        assertEquals(Optional.of(120), adapter.get("discussionTimerSeconds", Integer.class));
        assertTrue(adapter.get("nominationTimerSeconds", Integer.class).isEmpty());
    }

    @Test
    void loadClearsEntriesWhenConfigFileIsDeleted() throws IOException {
        Path configFile = tempDir.resolve("midnightcouncil.properties");
        Files.writeString(configFile, "discussionTimerSeconds=90\n");

        FabricConfigAdapter adapter = new FabricConfigAdapter(tempDir, "midnightcouncil.properties");
        adapter.load();
        assertEquals(Optional.of(90), adapter.get("discussionTimerSeconds", Integer.class));

        Files.delete(configFile);
        adapter.load();

        assertTrue(adapter.get("discussionTimerSeconds", Integer.class).isEmpty());
    }
}
