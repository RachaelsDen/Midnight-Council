package dev.kgoodwin.midnightcouncil.api;

public interface LoggerAdapter extends PlatformInterface {

	void debug(String message);

	void info(String message);

	void warn(String message);

	void error(String message);

	void error(String message, Throwable throwable);
}
