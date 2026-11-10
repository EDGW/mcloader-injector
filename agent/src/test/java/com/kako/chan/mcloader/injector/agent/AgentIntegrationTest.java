package com.kako.chan.mcloader.injector.agent;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs the packaged agent in a separate JVM against a real local HTTP endpoint. */
public class AgentIntegrationTest {
    private HttpServer server;
    private String base;
    private final BlockingQueue<String> requests = new LinkedBlockingQueue<>();

    @Before public void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String uri = exchange.getRequestURI().toString();
            requests.add(uri);
            byte[] body = uri.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(uri.contains("missing") ? 404 : 200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After public void stopServer() { server.stop(0); }

    @Test public void allThreeNetworkCallsRewriteWithoutMutatingUrl() throws Exception {
        for (String mode : new String[] {"connection", "proxy", "stream"}) {
            String original = "http://forward.invalid/source/" + mode + "?q=one%20two#fragment";
            String output = run(false, null, new String[] {
                    "-Dmcloader.injector.rule.1=http://forward.invalid/source/=>" + base + "/mirror/"}, mode, original);
            assertEquals("/mirror/" + mode + "?q=one%20two", requests.poll(5, TimeUnit.SECONDS));
            assertTrue(output, output.contains("ORIGINAL " + original));
            assertTrue(output, output.contains("BODY /mirror/" + mode + "?q=one%20two"));
            if (!"stream".equals(mode)) {
                assertTrue(output, output.contains("TARGET " + base + "/mirror/" + mode + "?q=one%20two#fragment"));
            }
        }
    }

    @Test public void isolatedProcessorUsesBootstrapBridgeAndPropertyPrecedence() throws Exception {
        String output = run(true, "not valid agent arguments", new String[] {
                "-Dmcloader.injector.table=http://forward.invalid/=>http://unused.invalid/",
                "-Dmcloader.injector.rule.processor=http://forward.invalid/source/=>" + base + "/isolated/"},
                "stream", "http://forward.invalid/source/mappings");
        assertEquals("/isolated/mappings", requests.poll(5, TimeUnit.SECONDS));
        assertTrue(output, output.contains("BODY /isolated/mappings"));
    }

    @Test public void agentArgumentsFallbackPreservesUnmatchedAndFileRequestsAndHttpErrors() throws Exception {
        String table = "http://forward.invalid/=>" + base + "/mirror/";
        String output = run(false, table, new String[0], "error", "http://forward.invalid/missing");
        assertEquals("/mirror/missing", requests.poll(5, TimeUnit.SECONDS));
        assertTrue(output, output.contains("EXPECTED FileNotFoundException"));
        output = run(false, table, new String[0], "stream", base + "/direct");
        assertEquals("/direct", requests.poll(5, TimeUnit.SECONDS));
        assertTrue(output, output.contains("BODY /direct"));
        Path file = Files.createTempFile("injector-fixture-", ".txt");
        try {
            Files.write(file, "local-content".getBytes(StandardCharsets.UTF_8));
            output = run(false, table, new String[0], "stream", file.toUri().toString());
            assertTrue(output, output.contains("BODY local-content"));
        } finally { Files.delete(file); }
    }

    @Test public void explicitProxyIsRetainedForRewrittenHost() throws Exception {
        String output = run(false, "http://forward.invalid/=>http://mirror.invalid/via/", new String[0],
                "explicit-proxy", "http://forward.invalid/library.jar?q=1");
        assertEquals("http://mirror.invalid/via/library.jar?q=1", requests.poll(5, TimeUnit.SECONDS));
        assertTrue(output, output.contains("TARGET http://mirror.invalid/via/library.jar?q=1"));
    }

    @Test public void loggingLevelsStayOnStderrWithoutChangingRequests() throws Exception {
        for (String level : new String[] {"default", "error", "info", "debug"}) {
            List<String> properties = new ArrayList<>();
            properties.add("-Dmcloader.injector.rule=http://forward.invalid/=>" + base + "/logs/");
            if (!"default".equals(level)) properties.add("-Dmcloader.injector.log=" + level);
            Output output = execute(false, null, properties.toArray(new String[0]), "stream",
                    "http://forward.invalid/" + level);
            assertEquals(output.stderr, 0, output.exitCode);
            assertEquals("/logs/" + level, requests.poll(5, TimeUnit.SECONDS));
            assertTrue(output.stdout, output.stdout.contains("BODY /logs/" + level));
            assertFalse(output.stdout, output.stdout.contains("[mcloader-injector]"));
            assertEquals(output.stderr, "info".equals(level) || "debug".equals(level),
                    output.stderr.contains("[mcloader-injector] INFO"));
            assertEquals(output.stderr, "debug".equals(level),
                    output.stderr.contains("[mcloader-injector] DEBUG"));
            assertFalse(output.stderr, output.stderr.contains("[mcloader-injector] ERROR"));
        }
    }

    @Test public void isolatedDebugLogsRedactBothUrlsAndDoNotReportNetworkErrors() throws Exception {
        String target = base.replace("http://", "http://target-user:target-password@") + "/mirror/";
        Output output = execute(true, null, new String[] {
                "-Dmcloader.injector.log=debug",
                "-Dmcloader.injector.rule=http://source-user:source-password@forward.invalid/=>" + target},
                "error", "http://source-user:source-password@forward.invalid/missing?token=secret-value#secret-fragment");
        assertEquals(output.stderr, 0, output.exitCode);
        assertEquals("/mirror/missing?token=secret-value", requests.poll(5, TimeUnit.SECONDS));
        assertTrue(output.stdout, output.stdout.contains("EXPECTED FileNotFoundException"));
        assertTrue(output.stderr, output.stderr.contains("[mcloader-injector] DEBUG"));
        assertTrue(output.stderr, output.stderr.contains("http://forward.invalid/missing"));
        assertTrue(output.stderr, output.stderr.contains(base + "/mirror/missing"));
        for (String secret : new String[] {"source-user", "source-password", "target-user", "target-password",
                "token", "secret-value", "secret-fragment"}) {
            assertFalse(output.stderr, output.stderr.contains(secret));
        }
        assertFalse(output.stderr, output.stderr.contains("[mcloader-injector] ERROR"));
    }

    @Test public void invalidLogLevelFailsBeforeInstallerMain() throws Exception {
        Output output = execute(false, "http://forward.invalid/=>" + base + "/mirror/", new String[] {
                "-Dmcloader.injector.log=invalid"}, "stream", "http://forward.invalid/file");
        assertNotEquals(0, output.exitCode);
        assertTrue(output.stderr, output.stderr.contains("[mcloader-injector] ERROR"));
        assertFalse(output.stdout, output.stdout.contains("ORIGINAL"));
    }

    private String run(boolean isolated, String table, String[] properties, String mode, String url) throws Exception {
        Output output = execute(isolated, table, properties, mode, url);
        assertEquals(output.stderr, 0, output.exitCode);
        return output.stdout + output.stderr;
    }

    private Output execute(boolean isolated, String table, String[] properties, String mode, String url) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("fixture.java"));
        command.addAll(Arrays.asList(properties));
        command.add("-javaagent:" + System.getProperty("injector.jar") + (table == null ? "" : "=" + table));
        command.add("-cp");
        command.add(System.getProperty("fixture.classes"));
        command.add(isolated ? "fixture.IsolatedClient" : "fixture.NetworkClient");
        if (isolated) command.add(System.getProperty("fixture.classes"));
        command.add(mode);
        command.add(url);
        if ("explicit-proxy".equals(mode)) command.add(Integer.toString(server.getAddress().getPort()));
        ProcessBuilder builder = new ProcessBuilder(command);
        for (String key : new String[] {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) builder.environment().remove(key);
        Process process = builder.start();
        try {
            assertTrue("Child JVM timed out", process.waitFor(30, TimeUnit.SECONDS));
            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            return new Output(process.exitValue(), stdout, stderr);
        } finally { if (process.isAlive()) process.destroyForcibly().waitFor(); }
    }

    private static String read(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = input) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class Output {
        final int exitCode;
        final String stdout;
        final String stderr;

        Output(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
