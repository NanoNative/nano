package org.nanonative.nano.helper;

import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.NanoServices;
import org.nanonative.nano.core.NanoThreads;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Scheduler;
import org.nanonative.nano.core.model.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"UnusedReturnValue", "java:S6548", "java:S2386"})
public class NanoUtils {

    public static final String LINE_SEPARATOR = System.lineSeparator();
    protected static Random random;
    public static final String[][] NANO_NAMES = {
        {"NanoNinja", "NanoNoodle", "GraalGuru", "JavaJester", "MicroMaverick", "ByteBender", "NanoNaut", "GraalGoblin", "JavaJuggernaut", "CodeComedian", "NanoNomad", "GraalGazelle", "JavaJinx", "MicroMagician", "ByteBandit", "NanoNimbus", "GraalGambler", "JavaJester", "MicroMaestro", "ByteBarracuda", "NanoNebula"},
        {"Swift Swiper", "Master", "Joker", "Rebel", "Twister", "Navigator", "Mischievous", "Unstoppable", "Laughs", "Wanderer", "Graceful", "Bringer", "Wizard", "Stealer", "Cloud Surfer", "Betting", "Prankster", "Conductor", "Feisty Fish", "Galactic Guardian"},
        {"of Requests", "of Native Magic", "in the Microservice Deck", "in the Server Space", "of Bytes", "of the Nano Cosmos", "Microservice Minion", "Force of the JVM", "in Lambda Expressions", "in the Backend Wilderness", "GraalVM Gazelle", "Bringer of Backend Blessings", "of the Microservice Realm", "of Server Secrets", "of the Nanoverse", "on Backend Brilliance", "in the Programming Playground", "of the Microservice Orchestra", "in the Server Sea", "of Microservices", "of Nano Power", "in Nano Land", "near Nano Destiny"}};
    public static final List<String> IGNORED_TRACE = List.of(
        "java.", "javax.", "sun.", "jdk.", "com.sun.",
        "org.junit.", "org.testng.", "org.gradle.", "org.apache.", "kotlin.", "scala."
    );
    // TIME UNITS
    public static final long NS = 1L;
    public static final long US = 1_000L;
    public static final long MS = 1_000_000L;
    public static final long S  = 1_000_000_000L;
    public static final long M  = 60L * S;
    public static final long H  = 60L * M;
    public static final long D  = 24L * H;
    public static final long W  = 7L * D;
    public static final long MO = 30L * D;   // calendar-agnostic
    public static final long Y  = 365L * D;
    public static final long[] UNIT_NS   = { Y,   MO,  W,  D,  H,  M,  S,  MS,  US,  NS };
    public static final String[] UNIT_SY = { "y", "mo","w","d","h","m","s","ms","µs","ns" };

    public static boolean hasText(final String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }

    public static String formatDuration(final long nanos) {
        if (nanos <= 0) return "0ns";

        long remaining = nanos;

        // Find the primary unit
        int idx = 0;
        while (idx < UNIT_NS.length && remaining < UNIT_NS[idx]) idx++;
        if (idx == UNIT_NS.length) return "0ns"; // shouldn’t happen

        final long primaryVal = remaining / UNIT_NS[idx];

        // For ns/µs/ms: standalone (no second unit)
        if (UNIT_NS[idx] <= MS) {
            return primaryVal + UNIT_SY[idx];
        }

        // For seconds and above: include exactly one lower unit (if non-zero)
        remaining -= primaryVal * UNIT_NS[idx];
        final int lowerIdx = Math.min(idx + 1, UNIT_NS.length - 1);
        final long lowerVal = remaining / UNIT_NS[lowerIdx];

        return lowerVal > 0
            ? primaryVal + UNIT_SY[idx] + " " + lowerVal + UNIT_SY[lowerIdx]
            : primaryVal + UNIT_SY[idx];
    }


    /**
     * Waits for a condition to become true, with actions on success or timeout.
     *
     * @param condition The condition to wait for, returning true when met.
     * @return true if the condition was met within the timeout, false otherwise.
     */
    public static boolean waitForCondition(final BooleanSupplier condition) {
        return waitForCondition(condition, 2000);
    }

    /**
     * Waits for a condition to become true, with actions on success or timeout.
     *
     * @param condition The condition to wait for, returning true when met.
     * @param timeout   stops waiting after period of time to unblock the test.
     * @return true if the condition was met within the timeout, false otherwise.
     */
    public static boolean waitForCondition(final BooleanSupplier condition, final long timeout) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            if (condition.getAsBoolean())
                return true;
            try {
                TimeUnit.MILLISECONDS.sleep(64);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private static boolean containsText(final CharSequence str) {
        final int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static String[] split(final String input, final String delimiter) {
        if (!input.contains(delimiter)) {
            return new String[]{input};
        }
        final List<String> result = new ArrayList<>();
        int start = 0;
        int index;
        while ((index = input.indexOf(delimiter, start)) != -1) {
            result.add(input.substring(start, index));
            start = index + delimiter.length();
        }
        result.add(input.substring(start));
        return result.toArray(new String[0]);
    }

    public static String callerInfoStr(final Class<?> source) {
        final StackTraceElement element = callerInfo(source);
        return element == null ? "Unknown" : String.format("%s:%d_at_%s", element.getClassName(), element.getLineNumber(), element.getMethodName());
    }

    public static StackTraceElement callerInfo(final Class<?> source) {
        final List<String> sourceNames = List.of(
            source.getName(),
            Service.class.getName(),
            NanoBase.class.getName(),
            NanoUtils.class.getName(),
            NanoThread.class.getName(),
            NanoThreads.class.getName(),
            NanoServices.class.getName()
        );
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (final StackTraceElement element : stackTrace) {
            if (!sourceNames.contains(element.getClassName()) && !element.getClassName().startsWith("java.lang.Thread")) {
                return element;
            }
        }
        return stackTrace.length > 2 ? stackTrace[2] : null;
    }

    public static String getThreadName(final ExecutorService executorService) {
        if (executorService instanceof final Scheduler scheduler) {
            return scheduler.id();
        } else if (executorService instanceof ScheduledExecutorService) {
            return "Scheduler";
        }
        return executorService.getClass().getSimpleName();
    }

    // ########## NANO CONFIGS ##########
    public static Context readConfigFiles(final Context context, final String profile) {
        final Context result = context != null ? context : Context.createRootContext(Nano.class);
        final List<String> scannedProfiles = result.asList(ArrayList::new, String.class, "_scanned_profiles");
        if (scannedProfiles.contains(profile))
            return result;
        if (!"".equals(profile))
            scannedProfiles.add(profile);
        result.put("_scanned_profiles", scannedProfiles);

        for (final String directory : new String[] {
            "",
            ".",
            "config/",
            ".config/",
            "resources/",
            ".resources/",
            "resources/config/",
            ".resources/config/"
        }) {
            readConfigFile(result, directory + "application" + (profile.isEmpty() ? profile : "-" + profile) + ".properties");
        }
        return readProfiles(result);
    }

    public static Context readProfiles(final Context result) {
        for (final String pConfig : new String[]{
            Context.CONFIG_PROFILES,
            "app_profile",
            "spring_profiles_active",
            "spring_profile_active",
            "profiles_active",
            "micronaut_profiles",
            "micronaut_environments"
        }) {
            result.asStringOpt(pConfig).ifPresent(profiles -> stream(split(profiles, ",")).map(String::trim).forEach(name -> readConfigFiles(result, name)));
        }
        return result;
    }

    public static Context readConfigFile(final Context context, final String path) {
        try (final InputStream input = path.startsWith(".") ? new FileInputStream(path.substring(1)) : NanoUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (input != null) {
                final Properties properties = new Properties();
                properties.load(input);
                properties.forEach((key, value) -> addConfig(context, key, value));
            }
        } catch (final Exception ignored) {
            // ignored
        }
        return context;
    }

    public static Context addConfig(final Context context, final Object key, final Object value) {
        if (value == null || "null".equals(value) || "".equals(value)) {
            context.remove(NanoBase.standardiseKey(key));
        } else if (value instanceof final String valueStr && hasText(valueStr)) {
            context.put(NanoBase.standardiseKey(key), valueStr.trim());
        } else {
            context.put(NanoBase.standardiseKey(key), value);
        }
        return context;
    }

    public static Context resolvePlaceHolders(final Context context) {
        context.forEach((key, value) -> {
            if (value instanceof final String valueStr && valueStr.startsWith("${") && valueStr.endsWith("}")) {
                final String[] placeholder = split(valueStr.substring(2, valueStr.length() - 1), ":");
                addConfig(context, key, context.asOpt(Object.class, NanoBase.standardiseKey(placeholder[0])).orElseGet(() -> placeholder.length > 1 ? placeholder[1].trim() : null));
            }
        });
        return context;
    }

    public static byte[] encodeGzip(final byte[] data) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data);
            gzipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (final IOException ignored) {
            return data;
        }
    }

    public static byte[] decodeZip(final byte[] data) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             final ZipInputStream gzipInputStream = new ZipInputStream(inputStream)) {
            return gzipInputStream.readAllBytes();
        } catch (final Exception ignored) {
            return data;
        }
    }

    public static byte[] decodeGzip(final byte[] data) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            return gzipInputStream.readAllBytes();
        } catch (final Exception ignored) {
            return data;
        }
    }

    public static byte[] encodeDeflate(final byte[] data) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outputStream)) {
            deflaterOutputStream.write(data);
            deflaterOutputStream.finish();
            return outputStream.toByteArray();
        } catch (final Exception ignored) {
            return data;
        }
    }

    public static byte[] decodeDeflate(final byte[] data) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             final InflaterInputStream inflaterInputStream = new InflaterInputStream(inputStream);
             final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            inflaterInputStream.transferTo(out);
            return out.toByteArray();
        } catch (final IOException ignored) {
            return data;
        }
    }

    public static String generateNanoName(final String format) {
        if (random == null) {
            random = new Random();
        }
        return String.format(format,
            NANO_NAMES[0][random.nextInt(NANO_NAMES[0].length)],
            random.nextInt(0, 99) + "." + random.nextInt(0, 9),
            NANO_NAMES[1][random.nextInt(NANO_NAMES[1].length)],
            NANO_NAMES[2][random.nextInt(NANO_NAMES[2].length)]
        );
    }

    /**
     * Handles a Java error by logging it and shutting down the application.
     * Note: it's likely that the application won't handle OOM errors. For OOM see {@link Context#CONFIG_OOM_SHUTDOWN_THRESHOLD}.
     *
     * @param context The context to use for logging and shutting down the application.
     * @param error   The error to handle.
     */
    @SuppressWarnings("java:S106") // Standard outputs used instead of logger
    public static void handleJavaError(final Supplier<Context> context, final Throwable error) {
        if (error instanceof Error) {
            ofNullable(context).map(Supplier::get).ifPresentOrElse(ctx -> ctx.fatal(error, () -> "It seems like the dark side of the JVM has struck again. Your scenario [{}]. May the garbage collector be with you!", error.getMessage()), () -> System.err.println(error.getMessage()));
            System.exit(1);
        }
    }

    public static void tryExecute(final Supplier<Context> context, final ExRunnable operation) {
        tryExecute(context, operation, null);
    }

    public static void tryExecute(final Supplier<Context> context, final ExRunnable operation, final Consumer<Throwable> consumer) {
        try {
            operation.run();
        } catch (final Throwable exception) {
            handleJavaError(context, exception);
            if (consumer != null) {
                consumer.accept(exception);
            }
        }
    }

    public static Stream<Path> listFiles(final Path path) {
        if (Files.isDirectory(path)) {
            try {
                return Files.list(path);
            } catch (final IOException ignored) {
                // ignored
            }
        }
        return Stream.of(path);
    }

    public static <T extends Throwable> T reduceSte(final T throwable) {
        if (throwable == null)
            return null;
        final String[] lastMethodKey = {null};
        final StackTraceElement[] original = throwable.getStackTrace();
        final StackTraceElement[] filtered = stream(throwable.getStackTrace())
            .filter(ste -> IGNORED_TRACE.stream().anyMatch(ste.getClassName()::startsWith))
            .filter(ste -> {
                final String methodKey = ste.getClassName() + "#" + ste.getMethodName();
                if (!methodKey.equals(lastMethodKey[0])) {
                    lastMethodKey[0] = methodKey;
                    return true;
                }
                return false;
            }).toArray(StackTraceElement[]::new);
        throwable.setStackTrace(filtered.length == 0 && original.length > 0 ? new StackTraceElement[]{original[0]} : filtered);
        return throwable;
    }

    private NanoUtils() {
        // static util class
    }
}
