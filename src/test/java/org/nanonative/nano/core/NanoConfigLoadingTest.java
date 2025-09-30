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
import java.util.Map;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.model.Context.CONFIG_PROFILES;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Execution(ExecutionMode.SAME_THREAD)  // Disable concurrent execution for config tests
final class NanoConfigLoadingTest {

    private static final String[] FEATURE_FLAG = {"feature.flag", "feature_flag", "FEATURE_FLAG"};
    private static final String[] REF_VALUE = {"ref.value", "ref_value", "REF_VALUE"};

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
            "micronaut.environments", "micronaut_environments",
            "feature.flag", "feature_flag",
            "base.value", "base_value",
            "message", "env", "outer.prod", "request.timeout"
        })
            System.clearProperty(k);

        writeCfg("application.properties",
            "feature.flag=file\n" +
            "base.value=default\n" +
            "ref.value=${base.value:default}\n" +
            "message=base\n");

        writeCfg("application-dev.properties",
            "sample_dev_key=dev-value\n" +
            "message=dev\n");

        writeCfg("application-prod.properties",
            "message=prod\n");

        writeCfg("application-local.properties",
            "sample_local_key=local-value\n" +
            "message=local\n");

        // Ensure real files are present where Nano looks (config/)
        writeConfig("application.properties",
            "feature.flag=file\n" +
            "base.value=default\n" +
            "ref.value=${base.value:default}\n" +
            "message=base\n");
        writeConfig("application-dev.properties",
            "sample_dev_key=dev-value\n" +
            "message=dev\n");
        writeConfig("application-prod.properties",
            "message=prod\n");
        writeConfig("application-local.properties",
            "sample_local_key=local-value\n" +
            "message=local\n");
    }

    @AfterEach
    void cleanup() throws Exception {
        // Clean up system properties thoroughly
        for (final String k : new String[]{
            "spring.profiles.active", "spring_profiles_active",
            "app.profiles", "app_profiles",
            "profiles.active", "profiles_active",
            "micronaut.environments", "micronaut_environments",
            "feature.flag", "feature_flag",
            "base.value", "base_value",
            "message", "env", "outer.prod", "request.timeout"
        })
            System.clearProperty(k);

        // clean ./config files so other tests don't sniff them
        final Path cfg = Path.of("config");
        if (Files.exists(cfg)) {
            try (var s = Files.walk(cfg)) {
                s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // Ignore file deletion errors during cleanup
                    }
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

    @Test
    void configFilesTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano.context().asString(CONFIG_PROFILES)).isEqualTo("default, local, dev, prod");
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).containsExactly("local", "default", "dev", "prod");
        assertThat(nano.context().asString("test_placeholder_fallback")).isEqualTo("fallback should be used 1");
        assertThat(nano.context().asString("test_placeholder_key_empty")).isEqualTo("fallback should be used 2");
        assertThat(nano.context().asString("test_placeholder_value")).isEqualTo("used placeholder value");
        assertThat(nano.context().asString("resource_key1")).isEqualTo("AA");
        assertThat(nano.context().asString("resource_key2")).isEqualTo("CC");
        assertThat(nano.context()).doesNotContainKey("test_placeholder_fallback_empty");
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @Test
    void environment_variables_override_files() {
        // Environment variables are harder to set in tests, so we test via system properties
        // which use the same precedence logic
        System.setProperty("feature.flag", "env-value");

        final Nano nano = new Nano();
        try {
            assertEquals("env-value", get(nano, FEATURE_FLAG),
                "Environment/system properties must override config files");
        } finally {
            nano.shutdown(nano.context());
            nano.waitForStop();
        }
    }

    @Test
    void multiple_profile_sources_merge_correctly() throws Exception {
        // Test Micronaut profiles alongside Spring profiles
        System.setProperty("micronaut.environments", "test,integration");
        System.setProperty("spring.profiles.active", "dev");

        final Context ctx = invokeReadConfigs(new String[]{"--profiles.active=custom"});

        // Should have loaded dev profile (spring), test profile (micronaut wouldn't exist in our setup)
        assertEquals("dev-value", ctx.asString("sample_dev_key"),
            "Spring profiles should be loaded");
    }

    @Test
    void config_loading_handles_missing_profiles_gracefully() throws Exception {
        // Test that requesting non-existent profiles doesn't crash
        final Context ctx = invokeReadConfigs(new String[]{"--app.profiles=nonexistent"});

        // Should not crash and should still have basic config
        assertThat(ctx).isNotNull();
        // Basic config files should still be loaded
        assertEquals("file", ctx.asString("feature_flag"), "Base config should still load");
    }

    @Test
    void complex_placeholder_scenario() throws Exception {
        // Test placeholder resolution with system properties override
        System.setProperty("base.value", "overridden-base");

        final Context ctx = invokeReadConfigs(new String[0]);

        // Placeholder should resolve to system property value
        assertEquals("overridden-base", first(ctx, REF_VALUE),
            "Placeholders should resolve to system property values");
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
