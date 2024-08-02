package org.nanonative.nano.core.model;

import org.junit.jupiter.api.Test;

import static org.nanonative.nano.helper.config.ConfigRegister.configDescriptionOf;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigTest {

    @Test
    void testNewConfig() {
        assertThat(registerConfig("AA:BB.CC-DD+ff", "ABC123")).isEqualTo("aa_bb_cc_dd_ff");
        assertThat(configDescriptionOf("AA:BB.CC-DD+ff")).isEqualTo("ABC123");
    }
}
