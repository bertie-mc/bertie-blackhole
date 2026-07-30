package com.berlord.bertieblackhole.config;

/** Thrown for anything wrong in bertie_blackhole.json; the message is shown in the log verbatim. */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
