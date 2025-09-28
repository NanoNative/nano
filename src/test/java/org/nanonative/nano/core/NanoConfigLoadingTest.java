package org.nanonative.nano.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.model.Context;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NanoConfigLoadingTest {

    // Aliases because Nano standardizes keys (., -, +, : → _ and lowercase)
    private static final String[] FEATURE_FLAG = {"feature.flag", "feature_flag", "FEATURE_FLAG"};
    private static final String[] BASE_VALUE = {"base.value", "base_value", "BASE_VALUE"};
    private static final String[] REF_VALUE = {"ref.value", "ref_value", "REF_VALUE"};
    private static final String[] MESSAGE = {"message", "MESSAGE"};

    private static String get(final Nano nano, final String... aliases) {
        for (final String k : aliases) {
            final String v = nano.context().asString(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    @Test
    void precedence_args_over_sys_over_files() {
        System.setProperty("feature.flag", "sys");

        final Nano nano = new Nano(new String[]{
            "--feature.flag=args", "--feature_flag=args" // either spelling
        });
        try {
            assertEquals("args", get(nano, FEATURE_FLAG), "CLI must win over -D and files");
        } finally {
            nano.shutdown(nano.context());
            nano.waitForStop();
        }
    }

    @Test
    void placeholders_resolve_after_all_sources() {
        System.setProperty("base.value", "from-sys");

        final Nano nano = new Nano();
        try {
            assertEquals("from-sys", get(nano, REF_VALUE),
                "Placeholders must resolve after overlays so -D is visible");
        } finally {
            nano.shutdown(nano.context());
            nano.waitForStop();
        }
    }


    @BeforeEach
    void setup() throws Exception {
        // purge System properties that could interfere
        for (final String k : new String[]{
            "spring.profiles.active", "spring_profiles_active",
            "app.profiles", "app_profiles",
            "profiles.active", "profiles_active",
            "feature.flag", "feature_flag",
            "base.value", "base_value",
            "message"
        })
            System.clearProperty(k);

        // Clean environment that could taint results
        System.clearProperty("spring.profiles.active");
        System.clearProperty("spring_profiles_active");
        System.clearProperty("app.profiles");
        System.clearProperty("app_profiles");
        System.clearProperty("profiles.active");
        System.clearProperty("profiles_active");
        System.clearProperty("feature.flag");
        System.clearProperty("feature_flag");
        System.clearProperty("base.value");
        System.clearProperty("base_value");
        System.clearProperty("message");


        // Write minimal config files where Nano looks by default: ./config
        writeCfg("application.properties", """
            feature.flag=file
            base.value=default
            ref.value=${base.value:default}
            message=base
            """);

        writeCfg("application-dev.properties", """
            sample_dev_key=dev-value
            message=dev
            """);

        writeCfg("application-prod.properties", """
            message=prod
            """);

        writeCfg("application-local.properties", """
            sample_local_key=local-value
            message=local
            """);

        // Ensure real files are present where Nano looks (config/)
        writeConfig("application.properties", """
            feature.flag=file
            base.value=default
            ref.value=${base.value:default}
            message=base
            """);
        writeConfig("application-dev.properties", """
            sample_dev_key=dev-value
            message=dev
            """);
        writeConfig("application-prod.properties", """
            message=prod
            """);
        writeConfig("application-local.properties", """
            sample_local_key=local-value
            message=local
            """);
    }

    @AfterEach
    void cleanup() throws Exception {
        // clean ./config files so other tests don’t sniff them
        final Path cfg = Path.of("config");
        if (Files.exists(cfg)) {
            try (var s = Files.walk(cfg)) {
                s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {Files.deleteIfExists(p);} catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    void readConfigs_loads_profiles_from_sys_and_args_and_preserves_order() throws Exception {
        // GIVEN: profiles from -D and CLI. Expect dev -> prod -> local (local wins on conflicts)
        System.setProperty("spring.profiles.active", "dev,prod");

        final Context ctx = invokeReadConfigs(
            new String[]{
                "--app.profiles=local",
                "--spring.profiles.active=dev,prod"
            }
        );

        // THEN: profile-only keys visible
        assertEquals("dev-value", ctx.asString("sample_dev_key"),
            "dev profile must be loaded via -D/CLI");

        assertEquals("local-value", ctx.asString("sample_local_key"),
            "local profile must be loaded via CLI");

        // AND: last profile (local) wins for overlapping 'message'
        assertEquals("local", first(ctx, MESSAGE),
            "last declared profile should win (dev, prod, local → local)");
    }

    @Test
    void readConfigs_applies_precedence_args_over_sys_over_files() throws Exception {
        // GIVEN: file default, -D override, CLI ultimate override
        System.setProperty("feature.flag", "sys");

        final Context ctx = invokeReadConfigs(
            new String[]{"--feature.flag=args", "--feature_flag=args"}
        );

        // THEN: args win
        assertEquals("args", first(ctx, FEATURE_FLAG),
            "precedence must be files < ENV < -D < CLI; CLI wins");
    }

    @Test
    void readConfigs_resolves_placeholders_after_all_overlays() throws Exception {
        // GIVEN: placeholder in base file, base.value overridden by -D
        System.setProperty("base.value", "from-sys");

        final Context ctx = invokeReadConfigs(new String[0]);

        // THEN: placeholder sees -D value
        assertEquals("from-sys", first(ctx, REF_VALUE),
            "placeholders must resolve AFTER profiles and overlays");
    }

    // ---- internals ----------------------------------------------------------

    private static Context invokeReadConfigs(final String[] args) throws Exception {
        // Real Nano instance (yes, it boots; we shut it down immediately after).
        final Nano nano = new Nano(args);
        try {
            final Method m = NanoBase.class.getDeclaredMethod("readConfigs", String[].class);
            m.setAccessible(true);
            final Object out = m.invoke(nano, (Object) (args == null ? new String[0] : Arrays.copyOf(args, args.length)));
            return (Context) out;
        } finally {
            nano.shutdown(nano.context());
            nano.waitForStop();
        }
    }

    private static String first(final Context ctx, final String... aliases) {
        for (final String k : aliases) {
            final String v = ctx.asString(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static void writeCfg(final String name, final String content) throws Exception {
        final Path dir = Path.of("config");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    static Path ensureConfigDir() throws IOException {
        final Path cfg = Path.of("config");
        if (!Files.exists(cfg)) Files.createDirectories(cfg);
        return cfg;
    }

    static void writeConfig(final String name, final String content) throws IOException {
        final Path cfg = ensureConfigDir();
        Files.writeString(cfg.resolve(name), content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
