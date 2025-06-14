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
import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_CERT;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KEY;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KTS;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_PASSWORD;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTP_PORT;

@SuppressWarnings("java:S112")
public class HttpsHelper {

    public static final String TYPE_PKCS_12 = "PKCS12";
    public static final String TYPE_JCEKS = "JCEKS";
    public static final String TYPE_JKS = "JKS";
    public static final String TYPE_TLS = "TLS";

    public static com.sun.net.httpserver.HttpServer createDefaultServer(final Context context) throws IOException {
        final int preferredPort = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).orElse(8080);
        return bindHttpServer(context, preferredPort);
    }

    public static com.sun.net.httpserver.HttpServer createHttpsServer(final Context context) throws IOException {
        final int preferredPort = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).orElse(8443);
        return bindHttpsServer(context, preferredPort);
    }

    private static com.sun.net.httpserver.HttpServer bindHttpServer(final Context context, final int preferredPort) throws IOException {
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

    private static com.sun.net.httpserver.HttpsServer bindHttpsServer(final Context context, final int preferredPort) throws IOException {
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
                readKts(context, password, keyStore);

                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, password);
                final SSLContext sslContext = SSLContext.getInstance(TYPE_TLS);
                sslContext.init(kmf.getKeyManagers(), null, null);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            } catch (Exception e) {
                context.error(() -> "Failed to configure HTTPS", e);
            }
            context.info(() -> "HTTPS configured on port [" + context.asInt(CONFIG_SERVICE_HTTP_PORT) + "]");
        }
    }

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

    public static void readKts(final Context context, final char[] password, final KeyStore keyStore) {
        context.asPathOpt(CONFIG_SERVICE_HTTPS_KTS).ifPresent(file -> {
            try (final InputStream is = Files.newInputStream(file)) {
                final String ktsType = Optional.of(file.toString().toLowerCase())
                    .map(name -> {
                        if (name.endsWith(".jks")) return TYPE_JKS;
                        if (name.endsWith(".jceks")) return TYPE_JCEKS;
                        if (name.endsWith(".p12") || name.endsWith(".pfx")) return TYPE_PKCS_12;
                        return TYPE_PKCS_12;
                    }).get();
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

    private static boolean isConversionNeeded(Path file) {
        try {
            final String content = Files.readString(file);
            return content.contains("-----BEGIN RSA PRIVATE KEY-----") ||
                content.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----");
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path convertKeyToPkcs8(Context context, Path originalKeyPath, String password) throws IOException, InterruptedException {
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

    private HttpsHelper() {
        // Utility class
    }
}
