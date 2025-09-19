package org.nanonative.nano.model;

import org.junit.jupiter.api.Test;
import org.nanonative.nano.helper.event.model.Channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.helper.event.model.Channel.isChannelIdAvailable;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;

class ChannelRegisterTest {

    @Test
    void testChannelChannelIdRegistration() {
        final Channel<Void, Void> channelId = registerChannelId(this.getClass().getSimpleName().toUpperCase(), Void.class, Void.class);
        assertThat(isChannelIdAvailable(channelId.id())).isTrue();
        assertThat(Channel.channelOf(channelId.name())).contains(channelId);

        // duplicated registration should not be possible
        final Channel<Void, Void> channelId2 = registerChannelId(this.getClass().getSimpleName().toUpperCase(), Void.class);
        assertThat(isChannelIdAvailable(channelId2.id())).isTrue();
        assertThat(channelId.id()).isEqualTo(channelId2.id());
        assertThat(channelId).isEqualTo(channelId2);
        assertThat(Channel.channelOf(channelId.id())).isEqualTo(channelId);
        assertThat(registerChannelId(this.getClass().getSimpleName().toUpperCase() + "2", Void.class)).isNotEqualTo(channelId);

        // should not find non registered channelIds
        assertThat(isChannelIdAvailable(-99)).isFalse();
    }
}
