package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.ConfigAdapter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricConfigAdapter implements ConfigAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricConfigAdapter.class);

    private final Path configPath;
    private final Map<String, String> entries = new ConcurrentHashMap<>();

    public FabricConfigAdapter(Path configDir, String fileName) {
        this.configPath = configDir.resolve(fileName);
    }

    @Override
    public void load() {
        if (!Files.exists(configPath)) {
            LOG.info("No config file at {}, using defaults", configPath);
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                entries.put(key, props.getProperty(key));
            }
            LOG.info("Loaded {} config entries from {}", entries.size(), configPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load config from " + configPath, e);
        }
    }

    @Override
    public void save() {
        Properties props = new Properties();
        entries.forEach(props::setProperty);
        try {
            Files.createDirectories(configPath.getParent());
            try (var out = Files.newOutputStream(configPath)) {
                props.store(out, "Midnight Council configuration");
            }
            LOG.debug("Saved config to {}", configPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save config to " + configPath, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> valueType) {
        String raw = entries.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        if (valueType == String.class) {
            return Optional.of((T) raw);
        }
        if (valueType == Integer.class) {
            return Optional.of((T) Integer.valueOf(raw));
        }
        if (valueType == Double.class) {
            return Optional.of((T) Double.valueOf(raw));
        }
        if (valueType == Boolean.class) {
            return Optional.of((T) Boolean.valueOf(raw));
        }
        if (valueType == Long.class) {
            return Optional.of((T) Long.valueOf(raw));
        }
        throw new IllegalArgumentException("Unsupported config value type: " + valueType.getName());
    }

    @Override
    public void set(String key, Object value) {
        entries.put(key, String.valueOf(value));
    }
}
