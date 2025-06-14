package org.nanonative.nano.services.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.nanonative.nano.core.model.Context;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Enumeration;

import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_CERT;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KEY;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KTS;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_PASSWORD;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTP_PORT;

/**
 * Utility class for configuring and creating HTTP and HTTPS servers using the built-in Java HTTP server APIs.
 * Primarily responsible for setting up SSL contexts and reading certificates/keys from the file system.
 */
@SuppressWarnings("java:S112")
public class HttpsHelper {

    public static final String TYPE_PKCS_12 = "PKCS12";
    public static final String TYPE_JCEKS = "JCEKS";
    public static final String TYPE_JKS = "JKS";
    public static final String TYPE_TLS = "TLS";

    /**
     * Creates a default HTTP server using the configured or fallback port.
     *
     * @param context configuration context, expected to provide CONFIG_SERVICE_HTTP_PORT
     * @return an HTTP server bound to the selected port
     * @throws IOException if the server cannot be created
     */
    public static com.sun.net.httpserver.HttpServer createDefaultServer(final Context context) throws IOException {
        final int preferredPort = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).orElse(8080);
        try {
            final com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(preferredPort), 0);
            context.put(CONFIG_SERVICE_HTTP_PORT, server.getAddress().getPort());
            return server;
        } catch (IOException ignored) {
            final com.sun.net.httpserver.HttpServer fallback = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
            context.put(CONFIG_SERVICE_HTTP_PORT, fallback.getAddress().getPort());
            return fallback;
        }
    }

    /**
     * Creates an HTTPS server using the configured or fallback port.
     * This does not configure the SSL context; call {@link #configureHttps(Context, com.sun.net.httpserver.HttpServer)} after creation.
     *
     * @param context configuration context, expected to provide CONFIG_SERVICE_HTTP_PORT
     * @return an HTTPS server bound to the selected port
     * @throws IOException if the server cannot be created
     */
    public static com.sun.net.httpserver.HttpServer createHttpsServer(final Context context) throws IOException {
        final int preferredPort = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).orElse(8443);
        try {
            final com.sun.net.httpserver.HttpsServer server = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(preferredPort), 0);
            context.put(CONFIG_SERVICE_HTTP_PORT, server.getAddress().getPort());
            return server;
        } catch (IOException ignored) {
            final com.sun.net.httpserver.HttpsServer fallback = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(0), 0);
            context.put(CONFIG_SERVICE_HTTP_PORT, fallback.getAddress().getPort());
            return fallback;
        }
    }

    /**
     * Configures SSL/TLS for an existing HTTPS server.
     * Loads certificates, keys, and keystores from the context.
     *
     * @param context configuration context with HTTPS-related keys
     * @param server  server to configure; must be an instance of HttpsServer
     */
    public static void configureHttps(final Context context, final com.sun.net.httpserver.HttpServer server) {
        if (server instanceof final HttpsServer httpsServer) {
            char[] password = context.asStringOpt(CONFIG_SERVICE_HTTPS_PASSWORD).map(String::toCharArray).orElse(null);
            try {
                final String ktsType = context.asPathOpt(CONFIG_SERVICE_HTTPS_KTS)
                    .map(Path::toString)
                    .map(String::toLowerCase)
                    .map(file -> {
                        if (file.endsWith(".jks")) return TYPE_JKS;
                        if (file.endsWith(".jceks")) return TYPE_JCEKS;
                        if (file.endsWith(".p12") || file.endsWith(".pfx")) return TYPE_PKCS_12;
                        return TYPE_PKCS_12;
                    }).orElse(TYPE_PKCS_12);
                final KeyStore keyStore = KeyStore.getInstance(ktsType);
                keyStore.load(null, password);

                final Certificate cert = readCertificate(context, keyStore);
                readKey(context, keyStore, password, cert);
                readKts(context, password, keyStore, ktsType);

                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, password);
                final SSLContext sslContext = SSLContext.getInstance(TYPE_TLS);
                sslContext.init(kmf.getKeyManagers(), null, null);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                context.info(() -> "HTTPS configured on port [" + context.asInt(CONFIG_SERVICE_HTTP_PORT) + "] with keystore [" + ktsType + "]");
            } catch (Exception e) {
                context.error(() -> "Failed to configure HTTPS", e);
            }
        }
    }

    /**
     * Reads a private key file (PEM or PKCS#8) and inserts it into the provided key store.
     * Converts keys to PKCS#8 format using OpenSSL if needed.
     *
     * @param context   configuration context
     * @param keyStore  target key store
     * @param password  key password
     * @param cert      certificate to associate with the private key
     */
    public static void readKey(final Context context, final KeyStore keyStore, final char[] password, final Certificate cert) {
        context.asPathOpt(CONFIG_SERVICE_HTTPS_KEY).ifPresent(file -> {
            Path keyFile = file;
            Path tempFile = null;
            try {
                if (isConversionNeeded(file)) {
                    tempFile = convertKeyToPkcs8(context, file, password != null ? new String(password) : null);
                    keyFile = tempFile;
                }

                byte[] keyBytes = Files.readAllBytes(keyFile);
                byte[] decodedKey = java.util.Base64.getDecoder().decode(new String(keyBytes).replaceAll("-----.*?-----", "").replaceAll("\\s", ""));
                final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
                final KeyFactory kf = KeyFactory.getInstance("RSA");
                final PrivateKey privateKey = kf.generatePrivate(spec);
                keyStore.setKeyEntry(file.getFileName().toString(), privateKey, password, new Certificate[]{cert});
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                context.error(e, () -> "Failed to load private key [" + file + "]");
                throw new IllegalArgumentException("Failed to load private key [" + file + "]", e);
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                        // ignored
                    }
                }
            }
        });
    }

    /**
     * Reads an X.509 certificate from a file and inserts it into the given key store.
     *
     * @param context  configuration context
     * @param keyStore target key store
     * @return the loaded certificate or null if loading failed
     */
    public static Certificate readCertificate(final Context context, final KeyStore keyStore) {
        return context.asPathOpt(CONFIG_SERVICE_HTTPS_CERT).map(file -> {
            try (final InputStream is = Files.newInputStream(file)) {
                final Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(is);
                keyStore.setCertificateEntry(file.getFileName().toString(), certificate);
                return certificate;
            } catch (final Exception e) {
                context.error(e, () -> "Failed to load X.509 [" + file + "]");
                return null;
            }
        }).orElse(null);
    }

    /**
     * Loads all entries from an existing keystore into another keystore.
     * Typically used to merge user-provided entries into a runtime key store.
     *
     * @param context  configuration context
     * @param password keystore password
     * @param keyStore target key store
     * @param ktsType  keystore type (e.g., JKS, JCEKS, PKCS12)
     */
    public static void readKts(final Context context, final char[] password, final KeyStore keyStore, final String ktsType) {
        context.asPathOpt(CONFIG_SERVICE_HTTPS_KTS).ifPresent(file -> {
            try (final InputStream is = Files.newInputStream(file)) {
                final KeyStore store = KeyStore.getInstance(ktsType);
                store.load(is, password);
                final Enumeration<String> aliases = store.aliases();
                while (aliases.hasMoreElements()) {
                    final String alias = aliases.nextElement();
                    final PrivateKey key = (PrivateKey) store.getKey(alias, password);
                    final Certificate[] chain = store.getCertificateChain(alias);
                    if (key != null && chain != null) {
                        keyStore.setKeyEntry(alias, key, password, chain);
                    }
                }
            } catch (final Exception e) {
                context.error(e, () -> "Failed to load keystore [" + file + "]");
            }
        });
    }

    /**
     * Determines if a key file needs conversion from legacy PEM formats to PKCS#8.
     *
     * @param file key file path
     * @return true if conversion is required, false otherwise
     */
    private static boolean isConversionNeeded(final Path file) {
        try {
            final String content = Files.readString(file);
            return content.contains("-----BEGIN RSA PRIVATE KEY-----") ||
                content.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----");
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Converts a PEM-formatted private key to unencrypted PKCS#8 using OpenSSL.
     * This method spawns a subprocess and captures its output.
     *
     * @param context         context for logging
     * @param originalKeyPath original PEM key file
     * @param password        optional password for key (null if not encrypted)
     * @return path to the converted temporary file
     * @throws IOException          if file operations fail
     * @throws InterruptedException if the OpenSSL process is interrupted
     */
    private static Path convertKeyToPkcs8(final Context context, final Path originalKeyPath, final String password) throws IOException, InterruptedException {
        final Path tempPkcs8 = Files.createTempFile("converted", ".key");
        final ProcessBuilder pb = new ProcessBuilder(
            "openssl", "pkcs8",
            "-topk8",
            "-inform", "PEM",
            "-in", originalKeyPath.toAbsolutePath().toString(),
            "-out", tempPkcs8.toAbsolutePath().toString(),
            "-nocrypt"
        );
        if (password != null) {
            pb.command().add("-passin");
            pb.command().add("pass:" + password);
        }
        pb.redirectErrorStream(true);
        final Process process = pb.start();
        final StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("OpenSSL key conversion failed, exit code: " + exitCode + "\nOutput:\n" + output + "\nCommand: " + String.join(" ", pb.command()));
        }
        context.info(() -> "Converted key to PKCS#8: " + tempPkcs8);
        return tempPkcs8;
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a static utility class.
     */
    private HttpsHelper() {
        // Utility class
    }
}
