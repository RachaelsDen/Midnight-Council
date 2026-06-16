package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.LoggerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricLoggerAdapter implements LoggerAdapter {

    private final Logger logger;

    public FabricLoggerAdapter(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }

    public FabricLoggerAdapter(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
