package com.kako.chan.mcloader.injector.agent;

import com.kako.chan.mcloader.injector.bridge.InjectorLog;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Transformation failures must be visible even when the JVM would ignore the transformer exception. */
public class NetworkTransformerTest {
    @Test public void malformedClassIsReportedWithoutSuppressingFailure() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream stderr = System.err;
        InjectorLog.configure(InjectorLog.ERROR);
        try (PrintStream output = new PrintStream(bytes, true, "UTF-8")) {
            System.setErr(output);
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> new NetworkTransformer().transform(getClass().getClassLoader(),
                            "fixture/Broken", null, null, new byte[0]));
            String log = bytes.toString("UTF-8");
            assertTrue(log, log.contains(InjectorLog.PREFIX + "ERROR"));
            assertTrue(log, log.contains("fixture/Broken"));
            assertTrue(log, log.contains(failure.getClass().getName()));
        } finally {
            System.setErr(stderr);
        }
    }
}
