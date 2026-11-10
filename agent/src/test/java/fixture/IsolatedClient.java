package fixture;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/** Simulates installer processors with no system-loader parent. */
public class IsolatedClient {
    public static void main(String[] args) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {new File(args[0]).toURI().toURL()}, null)) {
            Class<?> client = Class.forName("fixture.NetworkClient", true, loader);
            if (client.getClassLoader() != loader) throw new AssertionError("Fixture is not isolated");
            client.getMethod("main", String[].class).invoke(null, (Object) new String[] {args[1], args[2]});
        }
    }
}
