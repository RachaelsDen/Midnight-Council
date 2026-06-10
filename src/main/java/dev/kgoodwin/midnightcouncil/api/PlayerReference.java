package dev.kgoodwin.midnightcouncil.api;

import java.util.Objects;
import java.util.UUID;

public record PlayerReference(String value) {
    public PlayerReference {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static PlayerReference ofName(String value) {
        return new PlayerReference(value);
    }

    public static PlayerReference ofUuid(UUID uuid) {
        return new PlayerReference(Objects.requireNonNull(uuid, "uuid").toString());
    }

    public static PlayerReference from(UUID uuid) {
        return ofUuid(uuid);
    }
}
