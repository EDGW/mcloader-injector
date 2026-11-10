package com.kako.chan.mcloader.injector.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

/** Network entry points visible to installer and isolated processor class loaders. */
public final class UrlBridge {
    private static volatile Rules rules;

    private UrlBridge() {}

    /** Publishes the validated, longest-prefix-first forwarding table. */
    public static void install(String[] from, String[] to) {
        rules = new Rules(from.clone(), to.clone());
    }

    public static URLConnection openConnection(URL url) throws IOException {
        return rewrite(url).openConnection();
    }

    public static URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        return rewrite(url).openConnection(proxy);
    }

    public static InputStream openStream(URL url) throws IOException {
        return rewrite(url).openStream();
    }

    private static URL rewrite(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) return url;
        Rules current = rules;
        String value = url.toExternalForm();
        for (int i = 0; i < current.from.length; i++) {
            if (value.startsWith(current.from[i])) {
                if (current.from[i].equals(current.to[i])) return url;
                URL target = new URL(current.to[i] + value.substring(current.from[i].length()));
                InjectorLog.rewrite(url, target);
                return target;
            }
        }
        return url;
    }

    private static final class Rules {
        final String[] from;
        final String[] to;

        Rules(String[] from, String[] to) {
            this.from = from;
            this.to = to;
        }
    }
}
