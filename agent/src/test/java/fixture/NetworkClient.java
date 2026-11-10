package fixture;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

/** Real application call sites, deliberately outside the agent package. */
public class NetworkClient {
    public static void main(String[] args) throws Exception {
        URL original = new URL(args[1]);
        String before = original.toExternalForm();
        try {
            InputStream stream;
            if ("stream".equals(args[0]) || "error".equals(args[0])) {
                stream = original.openStream();
            } else {
                URLConnection connection;
                if ("explicit-proxy".equals(args[0])) {
                    Proxy proxy = new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress("127.0.0.1", Integer.parseInt(args[2])));
                    connection = original.openConnection(proxy);
                } else {
                    connection = "proxy".equals(args[0])
                            ? original.openConnection(Proxy.NO_PROXY) : original.openConnection();
                }
                if (!"file".equals(original.getProtocol()) && !(connection instanceof HttpURLConnection)) {
                    throw new AssertionError("Connection type changed");
                }
                System.out.println("TARGET " + connection.getURL());
                stream = connection.getInputStream();
            }
            try (InputStream input = stream) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                System.out.println("BODY " + new String(bytes.toByteArray(), "UTF-8"));
            }
        } catch (FileNotFoundException error) {
            if (!"error".equals(args[0])) throw error;
            System.out.println("EXPECTED FileNotFoundException");
        }
        if (!before.equals(original.toExternalForm())) throw new AssertionError("Original URL mutated");
        System.out.println("ORIGINAL " + original);
    }
}
