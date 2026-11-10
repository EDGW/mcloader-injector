package com.kako.chan.mcloader.injector.bridge;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import org.junit.Test;
import static org.junit.Assert.*;

/** Observable URL and connection semantics of the bootstrap forwarding boundary. */
public class UrlBridgeTest {
    @Test public void longestPrefixPreservesSuffixAndOriginalUrl() throws Exception {
        UrlBridge.install(new String[] {"https://origin.invalid/nested/", "https://origin.invalid/"},
                new String[] {"https://mirror.invalid/specific/", "https://mirror.invalid/general/"});
        URL source = new URL("https://origin.invalid/nested/file.jar?q=one%20two#entry");
        URLConnection result = UrlBridge.openConnection(source);
        assertTrue(result instanceof HttpURLConnection);
        assertEquals("https://mirror.invalid/specific/file.jar?q=one%20two#entry", result.getURL().toExternalForm());
        assertEquals("https://origin.invalid/nested/file.jar?q=one%20two#entry", source.toExternalForm());
    }

    @Test public void mappingIsSinglePass() throws Exception {
        UrlBridge.install(new String[] {"http://first.invalid/", "http://second.invalid/"},
                new String[] {"http://second.invalid/", "http://third.invalid/"});
        assertEquals("http://second.invalid/item", UrlBridge.openConnection(new URL("http://first.invalid/item"))
                .getURL().toExternalForm());
    }

    @Test public void identityAndUnmatchedUrlsKeepTheirObjects() throws Exception {
        UrlBridge.install(new String[] {"http://same.invalid/"}, new String[] {"http://same.invalid/"});
        URL identity = new URL("http://same.invalid/file");
        URL unmatched = new URL("http://other.invalid/file");
        URL local = new URL("file:/tmp/local.jar");
        URL resource = new URL("jar:file:/tmp/local.jar!/entry");
        assertSame(identity, UrlBridge.openConnection(identity).getURL());
        assertSame(unmatched, UrlBridge.openConnection(unmatched).getURL());
        assertSame(local, UrlBridge.openConnection(local).getURL());
        assertSame(resource, UrlBridge.openConnection(resource).getURL());
    }

    @Test public void installationDoesNotRetainMutableCallerArrays() throws Exception {
        String[] from = {"http://origin.invalid/"};
        String[] to = {"http://mirror.invalid/"};
        UrlBridge.install(from, to);
        from[0] = "http://different.invalid/";
        to[0] = "http://different.invalid/";
        assertEquals("http://mirror.invalid/item", UrlBridge.openConnection(new URL("http://origin.invalid/item"))
                .getURL().toExternalForm());
    }
}
