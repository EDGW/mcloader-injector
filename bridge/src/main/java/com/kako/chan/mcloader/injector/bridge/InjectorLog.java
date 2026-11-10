package com.kako.chan.mcloader.injector.bridge;

import java.net.URL;

/** Shared stderr logging, visible to the agent and isolated processors without dependencies. */
public final class InjectorLog {
    public static final String PREFIX = "[mcloader-injector] ";
    public static final int ERROR = 0;
    public static final int INFO = 1;
    public static final int DEBUG = 2;
    private static volatile int level = ERROR;

    private InjectorLog() {}

    /** Receives the level already parsed by the agent. */
    public static void configure(int value) {
        level = value;
    }

    public static boolean isDebugEnabled() {
        return level >= DEBUG;
    }

    public static void error(String message) {
        System.err.println(PREFIX + "ERROR " + message);
    }

    public static void info(String message) {
        if (level >= INFO) System.err.println(PREFIX + "INFO " + message);
    }

    public static void debug(String message) {
        if (isDebugEnabled()) System.err.println(PREFIX + "DEBUG " + message);
    }

    /** Describes only address rewriting, not connection or download completion. */
    public static void rewrite(URL source, URL target) {
        if (isDebugEnabled()) debug("rewrite " + address(source) + " -> " + address(target));
    }

    private static String address(URL url) {
        StringBuilder address = new StringBuilder(url.getProtocol()).append("://").append(url.getHost());
        if (url.getPort() != -1) address.append(':').append(url.getPort());
        return address.append(url.getPath()).toString();
    }
}
