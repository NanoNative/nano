package org.nanonative.nano.model;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;
import static java.util.Optional.ofNullable;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;

public class TestService extends Service {

    private final AtomicInteger startCount = new AtomicInteger(0);
    private final AtomicInteger stopCount = new AtomicInteger(0);
    private final List<Event<?, ?>> failures = new CopyOnWriteArrayList<>();
    private final List<Event<?, ?>> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<Consumer<Event<?, ?>>> doOnEvent = new AtomicReference<>();
    private final AtomicReference<Consumer<Event<?, ?>>> failureConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<Context>> startConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<Context>> stopConsumer = new AtomicReference<>();
    private long startTime = System.currentTimeMillis();
    public static Channel<Object, Object> TEST_EVENT = Channel.registerChannelId("TEST_EVENT", Object.class, Object.class);

    public TestService resetEvents() {
        events.clear();
        return this;
    }

    public <C, R> List<Event<C, R>> events(final Channel<C, R> channel) {
        getEvent(channel);
        return events.stream().map(event -> event.channel(channel)).filter(Optional::isPresent).map(Optional::get).toList();
    }

    public <C, R> Event<C, R> getEvent(final Channel<C, R> channel) {
        return getEvent(channel, null, 2000);
    }

    public <C, R> Event<C, R> getEvent(final Channel<C, R> channel, final Function<Event<C, R>, Boolean> condition) {
        return getEvent(channel, condition, TEST_TIMEOUT);
    }

    public <C, R> Event<C, R> getEvent(final Channel<C, R> channel, final long timeoutMs) {
        return getEvent(channel, null, timeoutMs);
    }

    public <C, R> Event<C, R> getEvent(final Channel<C, R> channel, final Function<Event<C, R>, Boolean> condition, final long timeoutMs) {
        final AtomicReference<Event<C, R>> result = new AtomicReference<>();
        waitForCondition(
            () -> {
                final Event<C, R> event1 = events.stream()
                    .map(event -> event.channel(channel))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(event -> condition != null ? condition.apply(event) : true)
                    .findFirst()
                    .orElse(null);
                if (event1 != null)
                    result.set(event1);
                return event1 != null;
            }
            , timeoutMs
        );
        if (result.get() == null)
            throw new AssertionError("Event not found"
                + " channel [" + channel.name() + "]"
                + " events [" + lineSeparator() + events.stream().filter(event -> event.channel() != EVENT_APP_HEARTBEAT).map(Event::toString).collect(Collectors.joining(lineSeparator())) + lineSeparator() + "]"
            );
        return result.get();
    }

    public int startCount() {
        return startCount.get();
    }

    public int stopCount() {
        return stopCount.get();
    }

    public List<Event<?, ?>> failures() {
        return failures;
    }

    public List<Event<?, ?>> events() {
        return events;
    }

    public Consumer<Event<?, ?>> doOnEvent() {
        return doOnEvent.get();
    }

    public TestService doOnEvent(final Consumer<Event<?, ?>> onEvent) {
        this.doOnEvent.set(onEvent);
        return this;
    }

    public Consumer<Event<?, ?>> doOnFailure() {
        return failureConsumer.get();
    }

    public TestService doOnFailure(final Consumer<Event<?, ?>> onFailure) {
        this.failureConsumer.set(onFailure);
        return this;
    }

    public Consumer<Context> doOnStart() {
        return startConsumer.get();
    }

    public TestService doOnStart(final Consumer<Context> onStart) {
        this.startConsumer.set(onStart);
        return this;
    }

    public Consumer<Context> doOnStop() {
        return stopConsumer.get();
    }

    public TestService doOnStop(final Consumer<Context> onStop) {
        this.stopConsumer.set(onStop);
        return this;
    }

    public long getStartTime() {
        return startTime;
    }

    // ########## DEFAULT METHODS ##########
    @Override
    public void start() {
        startTime = System.currentTimeMillis();
        startCount.incrementAndGet();
        if (startConsumer.get() != null)
            startConsumer.get().accept(context);
    }

    @Override
    public void stop() {
        stopCount.incrementAndGet();
        if (stopConsumer.get() != null)
            stopConsumer.get().accept(context);
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        failures.add(error);
        ofNullable(failureConsumer.get()).ifPresent(consumer -> consumer.accept(error));
        return null;
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        events.add(event);
        ofNullable(doOnEvent.get()).ifPresent(consumer -> consumer.accept(event));
    }

    @Override
    public void configure(final TypeMapI<?> configs, final TypeMapI<?> merged) {

    }
}
