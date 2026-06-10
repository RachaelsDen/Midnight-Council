package dev.kgoodwin.midnightcouncil.api;

import java.util.Optional;

public interface ConfigAdapter extends PlatformInterface {

	void load();

	void save();

	<T> Optional<T> get(String key, Class<T> valueType);

	void set(String key, Object value);
}
