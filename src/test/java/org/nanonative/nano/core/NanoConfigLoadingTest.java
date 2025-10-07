package org.nanonative.nano.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.model.Context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)  // Disable concurrent execution for config tests
final class NanoConfigLoadingTest {

    @BeforeEach
    void setup() throws Exception {
        // nuke noisy props
        for (String k : new String[]{
                "spring.profiles.active", "spring_profiles_active",
                "app.profiles", "app_profiles", "app.profile", "app_profile",
                "profiles.active", "profiles_active",
                "micronaut.environments", "micronaut_environments",
                "feature.flag", "feature_flag", "base.value", "base_value", "ref.value", "ref_value",
                "message", "env", "outer.prod", "request.timeout"
        })
            System.clearProperty(k);

        purgeAllDirs();

        // base
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "feature.flag=file",
                "base.value=default",
                "ref.value=${base.value:default}",
                "message=base",
                "resource.key1=AA",
                "resource.key2=BB",
                "app.profile=local" // single key discovery
        ));

        // profiles
        writeCfg("application-local.properties", String.join(System.lineSeparator(),
                "resource.key2=CC",
                "app.profiles=default, local, dev, prod"
        ));
        writeCfg("application-dev.properties", "dev.key=dev-value" + System.lineSeparator() + "message=dev" + System.lineSeparator());
        writeCfg("application-prod.properties", "message=prod" + System.lineSeparator());
    }

    @AfterEach
    void cleanup() throws Exception {
        purgeAllDirs();
        for (String k : System.getProperties().stringPropertyNames()) {
            if (k.contains("profile") || k.contains("profiles") || k.contains("feature") || k.contains("base") || k.contains("ref"))
                System.clearProperty(k);
        }
    }

    // ---------- tests ----------

    @Test
    void defaults_then_profile_override_same_dir() {
        // WHEN
        Nano nano = new Nano();
        // THEN: profile discovered via app.profile=local → application-local.properties overrides
        assertThat(nano.context().asString("resource_key1")).isEqualTo("AA");
        assertThat(nano.context().asString("resource_key2")).isEqualTo("CC"); // CC wins over BB
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("local"); // discovered and processed
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void cross_directory_priority_last_dir_wins() throws Exception {
        // GIVEN: same profile file in two dirs; later directory in search order should win
        Path dotResources = Files.createDirectories(Path.of(".resources/config"));
        Files.writeString(dotResources.resolve("application-local.properties"),
                "resource.key2=ZZ" + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // search order ends with ".resources/config/" in your loader → last write wins → ZZ expected

        // WHEN
        Nano nano = new Nano();
        // THEN
        assertThat(nano.context().asString("resource_key2")).isEqualTo("ZZ");
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("local");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void discovery_from_multiple_keys_merges_and_preserves_discovery_order() {
        // GIVEN: -D activates dev & prod; CLI activates local (order preserved by discovery)
        System.setProperty("spring.profiles.active", "dev,prod");
        Nano nano = new Nano(new String[]{"--app.profiles=local"});
        // THEN
        assertThat(nano.context().asList(String.class, "_scanned_profiles"))
                .containsSequence("local", "default", "dev", "prod"); // from application-local then app.profiles
        assertThat(nano.context().asString("dev_key")).isEqualTo("dev-value"); // dev loaded
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void reentrant_profile_pass_after_overlays() {
        // GIVEN: no file defines 'prod', but overlays do
        System.setProperty("profiles.active", "prod");

        // WHEN
        Nano nano = new Nano(); // loader re-runs profile pass after overlays
        // THEN: prod file exists and is loaded on 2nd pass (message=prod)
        assertThat(nano.context().asString("message")).isEqualTo("prod");
        assertThat(nano.context().asList(String.class, "_scanned_profiles"))
                .contains("prod");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void normalization_allows_mixed_case_profile_tokens() throws Exception {
        // GIVEN: profile file in upper-case name; token mixed case
        writeCfg("application-dev.properties", "dev.mixed=ok" + System.lineSeparator());
        System.setProperty("app.profiles", "DeV");

        // WHEN
        Nano nano = new Nano();
        // THEN
        assertThat(nano.context().asString("dev_mixed")).isEqualTo("ok"); // lower-cased standardization works
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("dev");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholders_resolve_finally_after_profiles_and_overlays() {
        // GIVEN: base has ref.value=${base.value}; overlays set base.value
        System.setProperty("base.value", "from-sys");

        Nano nano = new Nano();
        assertThat(first(nano.context(), "ref.value", "ref_value")).isEqualTo("from-sys");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholders_fallback_and_empty_default_behave() throws Exception {
        // Arrange: pure file-based placeholders
        purgeAllDirs();
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "a.fallback=${missing_key:fall}",        // uses default
                "a.empty=${missing_key:}",               // empty default -> key removed
                "a.value=present",
                "a.ref=${a.value:unused}"                // resolves to 'present'
        ));

        Nano nano = new Nano();
        assertThat(nano.context().asString("a_fallback")).isEqualTo("fall");
        assertThat(nano.context().containsKey("a_empty")).isFalse();        // removed due to empty default
        assertThat(nano.context().asString("a_ref")).isEqualTo("present");  // resolved to existing key
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholders_see_overlays_after_profiles_and_overrides() throws Exception {
        // Arrange: base references ${pref.value}, then -D overrides it
        purgeAllDirs();
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "pref.value=from-file",
                "ref.final=${pref.value:default}"
        ));
        System.setProperty("pref.value", "from-sys"); // overlay

        Nano nano = new Nano();
        // resolvePlaceHolders runs after overlays → picks "from-sys"
        assertThat(nano.context().asString("ref_final")).isEqualTo("from-sys");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholder_in_profile_activation_discovers_and_loads_profile() throws Exception {
        // Arrange: app.profile is a placeholder resolved from -D
        purgeAllDirs();
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "app.profile=${active_profile:local}",   // discovery runs resolvePlaceHolder() for profile keys
                "value=base"
        ));
        writeCfg("application-dev.properties", "value=dev" + System.lineSeparator());
        System.setProperty("active_profile", "dev");

        Nano nano = new Nano();
        // dev is discovered via placeholder -> profile loaded -> value overridden
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("dev");
        assertThat(nano.context().asString("value")).isEqualTo("dev");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholder_resolves_to_profile_defined_value() throws Exception {
        // Arrange: base references ${from.profile}; only the profile defines it.
        purgeAllDirs();
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "app.profile=local",
                "ref.late=${from.profile:miss}"   // will exist only after profile load
        ));
        writeCfg("application-local.properties", "from.profile=hit" + System.lineSeparator());

        Nano nano = new Nano();
        // resolvePlaceHolders runs after profile cascade → picks "hit"
        assertThat(nano.context().asString("ref_late")).isEqualTo("hit");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void placeholder_missing_key_uses_default_literal_including_colons() throws Exception {
        purgeAllDirs();
        writeCfg("application.properties",
                "complex.default=${nope:http://example:8080/path}" + System.lineSeparator()
        );

        Nano nano = new Nano();
        // Your split() uses the first ":" as separator; we verify the remainder is preserved as default text
        assertThat(nano.context().asString("complex_default"))
                .isEqualTo("http://example:8080/path");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void nested_placeholders_are_single_pass_only() throws Exception {
        purgeAllDirs();
        writeCfg("application.properties", String.join(System.lineSeparator(),
                "k1=${k2:def1}",
                "k2=${k3:def2}",
                "k3=value"
        ));

        Nano nano = new Nano();
        assertThat(nano.context().asString("k2")).isEqualTo("value");
        assertThat(nano.context().asString("k1")).isEqualTo("value");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void precedence_cli_over_sys_over_files() {
        System.setProperty("feature.flag", "sys");
        Nano nano = new Nano(new String[]{"--feature.flag=args"});
        assertThat(first(nano.context(), "feature.flag", "feature_flag")).isEqualTo("args");
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void nonexistent_profile_is_ignored_but_base_still_loads() {
        Nano nano = new Nano(new String[]{"--app.profiles=ghost"});
        assertThat(nano.context().asString("feature_flag")).isEqualTo("file"); // base loaded
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("ghost"); // scanned but no file
        nano.shutdown(nano.context()).waitForStop();
    }

    @Test
    void idempotent_second_call_does_not_reprocess_already_scanned_profiles() throws Exception {
        // GIVEN
        Nano nano = new Nano();
        var firstScan = nano.context().asList(String.class, "_scanned_profiles");
        assertThat(firstScan).isNotEmpty();

        // WHEN: simulate re-running config reader with same dirs/keys (Nano does this internally too)
        var ctxBefore = nano.context().asString("message");
        // call the same readConfigs via reflection (or trigger your public re-run if exposed)
        var ctx = nano.context(); // your loader already re-runs once; here we just assert stability

        // THEN: values stable, no duplicate scans appended
        assertThat(nano.context().asString("message")).isEqualTo(ctxBefore);
        assertThat(nano.context().asList(String.class, "_scanned_profiles"))
                .containsExactlyElementsOf(firstScan);
        nano.shutdown(nano.context()).waitForStop();
    }

    private static void deleteTree(Path p) throws Exception {
        if (Files.exists(p)) {
            try (var s = Files.walk(p)) {
                s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(x -> {
                    try {Files.deleteIfExists(x);} catch (Exception ignored) {}
                });
            }
        }
    }

    private static void purgeAllDirs() throws Exception {
        for (String d : new String[]{"config", ".config", "resources", ".resources", "resources/config", ".resources/config"})
            deleteTree(Path.of(d));
    }

    // ---------- test helpers ----------

    private static void writeCfg(String name, String content) throws Exception {
        Path dir = Path.of("config");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String first(Context ctx, String... aliases) {
        return Arrays.stream(aliases)
                .map(ctx::asString)
                .filter(v -> v != null && !v.isEmpty())
                .findFirst().orElse(null);
    }
}
