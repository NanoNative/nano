package org.nanonative.nano.services.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.NanoUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_CERTS;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_PASSWORD;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTP_PORT;
import static org.nanonative.nano.services.http.HttpServer.nextFreePort;

@SuppressWarnings("java:S112")
public class HttpsHelper {

    public static synchronized com.sun.net.httpserver.HttpServer createHttpServer(final Context context) throws IOException {
        final String certPaths = context.asStringOpt(CONFIG_SERVICE_HTTPS_CERTS).orElse(null);
        if (certPaths == null)
            return createDefaultServer(context);
        try {
            final List<Path> validPaths = Arrays.stream(certPaths.split(",")).map(String::trim).filter(NanoUtils::hasText).map(Paths::get).filter(Files::exists).toList();
            if (validPaths.isEmpty()) {
                context.error(() -> "No valid certificate paths [{}]", certPaths);
                createDefaultServer(context);
            }
            return initializeSSLContext(context, validPaths, context.asStringOpt(CONFIG_SERVICE_HTTPS_PASSWORD).orElse(""));
        } catch (Exception e) {
            context.error(e, () -> "Failed to initialize HTTPS configuration");
            return createDefaultServer(context);
        }
    }

    private static HttpsServer initializeSSLContext(final Context context, final List<Path> paths, final String password) throws Exception {
        final KeyStore keyStore = loadKeyStore(context, paths, password);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        // Create HTTPS server with the SSL context
        final int port = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).filter(p -> p > 0).orElseGet(() -> nextFreePort(8443));
        context.put(CONFIG_SERVICE_HTTP_PORT, port);
        final HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        context.info(() -> "HTTPS server configured with [{}]", paths);
        return httpsServer;
    }

    public static com.sun.net.httpserver.HttpServer createDefaultServer(final Context context) throws IOException {
        final int port = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).filter(p -> p > 0).orElseGet(() -> nextFreePort(8080));
        context.put(CONFIG_SERVICE_HTTP_PORT, port);
        return com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
    }

    public static KeyStore loadKeyStore(final Context context, final List<Path> paths, final String password) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        for (final Path path : paths) {
            if (Files.isDirectory(path)) {
                // Handle directory of certificates
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.{crt,pem,pfx,jks,key}")) {
                    for (Path file : stream)
                        loadCertificateOrKeyFile(context, keyStore, file, password);
                }
            } else {
                // Handle single file
                loadCertificateOrKeyFile(context, keyStore, path, password);
            }
        }
        return keyStore;
    }

    public static void loadCertificateOrKeyFile(final Context context, final KeyStore keyStore, final Path file, String password) throws Exception {
        final String fileName = file.getFileName().toString().toLowerCase();
        final String alias = file.getFileName().toString();

        if (fileName.endsWith(".jks")) {
            loadJksStore(context, keyStore, file, password);
        } else if (fileName.endsWith(".pfx") || fileName.endsWith(".p12")) {
            loadPkcs12Store(context, keyStore, file, password);
        } else if (fileName.endsWith(".key")) {
            final Certificate[] chain = keyStore.getCertificateChain(alias.replace(".key", ""));
            if (chain != null)
                keyStore.setKeyEntry(alias, loadPrivateKey(context, file), password.toCharArray(), chain);
        } else {
            // Assume PEM/CRT format certificate
            loadPemCertificate(context, keyStore, file, alias);
        }
    }

    private static void loadJksStore(final Context context, final KeyStore targetStore, final Path jksFile, final String password) throws KeyStoreException {
        final KeyStore jksStore = KeyStore.getInstance("JKS");
        try (final InputStream is = Files.newInputStream(jksFile)) {
            jksStore.load(is, password.toCharArray());
            for (final String alias : Collections.list(jksStore.aliases())) {
                if (jksStore.isKeyEntry(alias)) {
                    targetStore.setKeyEntry(alias,
                        jksStore.getKey(alias, password.toCharArray()),
                        password.toCharArray(),
                        jksStore.getCertificateChain(alias)
                    );
                } else {
                    targetStore.setCertificateEntry(alias, jksStore.getCertificate(alias));
                }
            }
        } catch (final Exception e) {
            context.error(e, () -> "Failed to load JKS [" + jksFile + "]");
        }
    }

    private static void loadPkcs12Store(final Context context, final KeyStore targetStore, final Path pkcs12File, final String password) throws KeyStoreException {
        KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(pkcs12File)) {
            pkcs12Store.load(is, password.toCharArray());
            // Copy all entries
            for (String alias : Collections.list(pkcs12Store.aliases())) {
                if (pkcs12Store.isKeyEntry(alias)) {
                    targetStore.setKeyEntry(alias,
                        pkcs12Store.getKey(alias, password.toCharArray()),
                        password.toCharArray(),
                        pkcs12Store.getCertificateChain(alias));
                } else {
                    targetStore.setCertificateEntry(alias, pkcs12Store.getCertificate(alias));
                }
            }
        } catch (final Exception e) {
            context.error(e, () -> "Failed to load PKCS12 [" + pkcs12File + "]");
        }
    }

    private static void loadPemCertificate(final Context context, final KeyStore keyStore, final Path certFile, final String alias) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = Files.newInputStream(certFile)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            keyStore.setCertificateEntry(alias, cert);
        } catch (final Exception e) {
            context.error(e, () -> "Failed to load X.509 [" + certFile + "]");
        }
    }

    private static PrivateKey loadPrivateKey(final Context context, final Path keyPath) throws Exception {
        byte[] keyBytes = Files.readAllBytes(keyPath);

        // Try PKCS8 format first
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            // If PKCS8 fails, try other common formats
            for (String algorithm : new String[]{"RSA", "EC", "DSA"}) {
                try {
                    return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
                } catch (Exception ignored) {
                    // Continue trying next algorithm
                }
            }
            context.error(e, () -> "Failed to load KEY [" + keyPath + "] - unsupported format");
            return null;
        }
    }

    private HttpsHelper() {
        // Utility class
    }
}
