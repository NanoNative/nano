package org.nanonative.nano.helper.logger.logic;

import berlin.yuna.typemap.model.Pair;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.nanonative.nano.core.model.Context.CONFIG_LOG_QUEUE_SIZE;
import static org.nanonative.nano.core.model.Context.CONTEXT_LOG_QUEUE_KEY;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;

@SuppressWarnings("UnusedReturnValue")
public class LogQueue extends Service {
    protected BlockingQueue<Pair<Logger, LogRecord>> queue;
    protected int queueCapacity;

    public LogQueue() {
        super(null, false);
    }

    public boolean log(final Logger logger, final LogRecord logRecord) {
        if (isReady() && queue != null) {
            try {
                queue.put(new Pair<>(logger, logRecord));
                return true;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    @Override
    public void start(final Supplier<Context> contextSub) {
        isReady.set(false, true, state -> {
            final Context context = contextSub.get();
            queueCapacity = context.asIntOpt(CONFIG_LOG_QUEUE_SIZE).orElse(1000);
            queue = new LinkedBlockingQueue<>(queueCapacity);
            context.run(this::process)
                .run(this::checkQueueSizeAndWarn, 5, 5, TimeUnit.MINUTES, () -> !isReady())
                .broadcastEvent(EVENT_CONFIG_CHANGE, Map.of(CONTEXT_LOG_QUEUE_KEY, this));
        });
    }

    @Override
    public void stop(final Supplier<Context> contextSub) {
        isReady.set(true, false, state -> {
            try {
                contextSub.get().broadcastEvent(EVENT_CONFIG_CHANGE, Map.of(CONTEXT_LOG_QUEUE_KEY, this));
                logger.debug(() -> "Shutdown initiated - process last messages [{}]", queue.size());
                queue.put(new Pair<>(logger.javaLogger(), new LogRecord(Level.INFO, "Shutdown Hook")));
                queue = null;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void onEvent(final Event event) {
        // Prevent from setting log queue to itself
        event.ifPresent(EVENT_CONFIG_CHANGE, TypeMap.class, map -> {
            map.remove(CONTEXT_LOG_QUEUE_KEY);
            logger.configure(map);
        });
    }

    @Override
    public Object onFailure(final Event error) {
        return null;
    }

    protected void process() {
        while (isReady() || (queue != null && !queue.isEmpty())) {
            try {
                final Pair<Logger, LogRecord> pair = queue.take();
                if (pair.key() != this.logger.javaLogger()) {
                    pair.key().log(pair.value());
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void checkQueueSizeAndWarn() {
        isReady.run(true, state -> {
            if (queue != null) {
                final int size = queue.size();
                final int percentage = size > 0 ? ((int) ((double) size / queueCapacity) * 100) : 0;
                if (percentage > 80) {
                    logger.warn(() -> "Warning: Log queue is " + percentage + "% full.");
                }
            }
        });
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "size=" + queue.size() +
            ", max=" + queueCapacity +
            '}';
    }
}



