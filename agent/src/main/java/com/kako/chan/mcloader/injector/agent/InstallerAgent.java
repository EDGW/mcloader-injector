package com.kako.chan.mcloader.injector.agent;

import com.kako.chan.mcloader.injector.bridge.InjectorLog;
import com.kako.chan.mcloader.injector.bridge.UrlBridge;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/** Loads the bootstrap bridge and registers application-only load-time instrumentation. */
public final class InstallerAgent {
    // Keep the appended archive open for bootstrap class loading throughout the JVM lifetime.
    private static JarFile bootstrapJar;

    private InstallerAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
        try {
            int logLevel = logLevel();
            RuleTable table = RuleTable.parse(agentArgs, System.getProperties());
            Path bridge = Files.createTempFile("mcloader-injector-bridge-", ".jar");
            bridge.toFile().deleteOnExit();
            try (InputStream resource = InstallerAgent.class.getResourceAsStream("/bootstrap/bridge.jar")) {
                if (resource == null) throw new IOException("Agent JAR is missing bootstrap/bridge.jar");
                Files.copy(resource, bridge, StandardCopyOption.REPLACE_EXISTING);
            }
            bootstrapJar = new JarFile(bridge.toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bootstrapJar);
            InjectorLog.configure(logLevel);
            UrlBridge.install(table.from, table.to);
            instrumentation.addTransformer(new NetworkTransformer());
            InjectorLog.info("initialized with " + table.from.length + " forwarding rules");
        } catch (IOException | RuntimeException | LinkageError failure) {
            // Bootstrap failures must be reportable before InjectorLog can be loaded.
            System.err.println(InjectorLog.PREFIX + "ERROR initialization failed: " + failure.getClass().getName());
            throw failure;
        }
    }

    private static int logLevel() {
        switch (System.getProperty("mcloader.injector.log", "error")) {
            case "error": return InjectorLog.ERROR;
            case "info": return InjectorLog.INFO;
            case "debug": return InjectorLog.DEBUG;
            default: throw new IllegalArgumentException("mcloader.injector.log must be error, info or debug");
        }
    }
}
