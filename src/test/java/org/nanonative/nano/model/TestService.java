package org.nanonative.nano.model;

import org.nanonative.nano.core.config.TestConfig;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.EventChannelRegister;
import org.nanonative.nano.helper.event.model.Event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;

public class TestService extends Service {

    private final AtomicInteger startCount = new AtomicInteger(0);
    private final AtomicInteger stopCount = new AtomicInteger(0);
    private final List<Event> failures = new CopyOnWriteArrayList<>();
    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<Consumer<Event>> doOnEvent = new AtomicReference<>();
    private final AtomicReference<Consumer<Event>> failureConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<Context>> startConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<Context>> stopConsumer = new AtomicReference<>();
    private long startTime = System.currentTimeMillis();
    public static int TEST_EVENT = EventChannelRegister.registerChannelId("TEST_EVENT");

    public TestService resetEvents() {
        events.clear();
        return this;
    }

    public List<Event> events(final int channelId) {
        getEvent(channelId);
        return events.stream().filter(event -> event.channelId() == channelId).toList();
    }

    public Event getEvent(final int channelId) {
        return getEvent(channelId, null, 2000);
    }

    public Event getEvent(final int channelId, final Function<Event, Boolean> condition) {
        return getEvent(channelId, condition, TestConfig.TEST_TIMEOUT);
    }

    public Event getEvent(final int channelId, final long timeoutMs) {
        return getEvent(channelId, null, timeoutMs);
    }

    public Event getEvent(final int channelId, final Function<Event, Boolean> condition, final long timeoutMs) {
        final AtomicReference<Event> result = new AtomicReference<>();
        waitForCondition(
            () -> {
                final Event event1 = events.stream()
                    .filter(event -> event.channelId() == channelId)
                    .filter(event -> condition != null ? condition.apply(event) : true)
                    .findFirst()
                    .orElse(null);
                if (event1 != null)
                    result.set(event1);
                return event1 != null;
            }
            , timeoutMs
        );
        return result.get();
    }

    public int startCount() {
        return startCount.get();
    }

    public int stopCount() {
        return stopCount.get();
    }

    public List<Event> failures() {
        return failures;
    }

    public List<Event> events() {
        return events;
    }

    public Consumer<Event> doOnEvent() {
        return doOnEvent.get();
    }

    public TestService doOnEvent(final Consumer<Event> onEvent) {
        this.doOnEvent.set(onEvent);
        return this;
    }

    public Consumer<Event> doOnFailure() {
        return failureConsumer.get();
    }

    public TestService doOnFailure(final Consumer<Event> onFailure) {
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
    public Object onFailure(final Event error) {
        failures.add(error);
        ofNullable(failureConsumer.get()).ifPresent(consumer -> consumer.accept(error));
        return null;
    }

    @Override
    public void onEvent(final Event event) {
        events.add(event);
        ofNullable(doOnEvent.get()).ifPresent(consumer -> consumer.accept(event));
    }
}
