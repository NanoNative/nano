package org.nanonative.nano.model;

import org.junit.jupiter.api.Test;

import static org.nanonative.nano.helper.event.EventChannelRegister.*;
import static org.assertj.core.api.Assertions.assertThat;

class EventChannelRegisterTest {

    @Test
    void testChannelChannelIdRegistration() {
        final int channelId = registerChannelId(this.getClass().getSimpleName().toUpperCase());
        assertThat(isChannelIdAvailable(channelId)).isTrue();
        assertThat(eventNameOf(channelId)).isEqualTo(this.getClass().getSimpleName().toUpperCase());

        // duplicated registration should not be possible
        final int channelId2 = registerChannelId(this.getClass().getSimpleName().toUpperCase());
        assertThat(isChannelIdAvailable(channelId2)).isTrue();
        assertThat(channelId).isEqualTo(channelId2);
        assertThat(eventNameOf(channelId)).isEqualTo(this.getClass().getSimpleName().toUpperCase());

        // should not find non registered channelIds
        assertThat(isChannelIdAvailable(-99)).isFalse();
    }
}
