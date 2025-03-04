package org.nanonative.nano.services.logging;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.logging.model.LogLevel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.helper.event.EventChannelRegister.registerChannelId;

public class LogService extends Service {

    // CONFIG
    public static final String CONFIG_LOG_LEVEL = registerConfig("app_log_level", "Log level for the application (see " + LogLevel.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_FORMATTER = registerConfig("app_log_formatter", "Log formatter (see " + LogFormatRegister.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_EXCLUDE_PATTERNS = registerConfig("app_log_excludes", "Exclude patterns for logger names");

    // CHANNEL
    public static final int EVENT_LOGGING = registerChannelId("EVENT_LOGGING");

    public static final AtomicInteger MAX_LOG_NAME_LENGTH = new AtomicInteger(10);
    protected List<String> excludePatterns;
    protected Formatter logFormatter = new LogFormatterConsole();
    protected Level level = Level.FINE;

    @Override
    public void start() {
        // nothing to do
    }

    @Override
    public void stop() {
        // nothing to do
    }

    @Override
    public Object onFailure(final Event error) {
        return null;
    }

    @Override
    public void onEvent(final Event event) {
        event.filter(this::isLoggable).ifPresent(
            event1 -> context.run(() -> event1.ifPresentAck(EVENT_LOGGING, LogRecord.class, this::log))
        );
    }

    @Override
    public void configure(final TypeMapI<?> configs, final TypeMapI<?> merged) {
        merged.asOpt(LogLevel.class, CONFIG_LOG_LEVEL).map(LogLevel::toJavaLogLevel).ifPresent(this::level);
        merged.asOpt(Formatter.class, CONFIG_LOG_FORMATTER).ifPresent(this::formatter);
        excludePatterns = merged.asStringOpt(CONFIG_LOG_EXCLUDE_PATTERNS).map(patterns -> Arrays.stream(patterns.split(",")).map(String::trim).toList()).orElseGet(List::of);
    }

    public synchronized LogService level(final Level level) {
        this.level = level;
        return this;
    }

    public Level level() {
        return level;
    }

    public synchronized LogService formatter(final Formatter formatter) {
        this.logFormatter = formatter;
        return this;
    }

    public Formatter formatter() {
        return this.logFormatter;
    }

    public void log(final Supplier<LogRecord> logRecord) {
        context.run(() -> log(logRecord.get()));
    }

    @SuppressWarnings("SameReturnValue")
    protected boolean log(final LogRecord logRecord) {
        context.run(() -> {
            final String formattedMessage = logFormatter.format(logRecord);
            if (logRecord.getLevel().intValue() < Level.WARNING.intValue()) {
                System.out.print(formattedMessage);
            } else {
                System.err.print(formattedMessage);
            }
        });
        return true;
    }

    private boolean isLoggable(final Event event) {
        return event.channelId() == EVENT_LOGGING && event.asOpt(Level.class, "level")
            .filter(level -> level.intValue() > this.level.intValue())
            .map(level -> event.asString("name"))
            .filter(name -> excludePatterns == null || excludePatterns.stream().noneMatch(name::contains))
            .isPresent();
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("name", this.getClass().getSimpleName())
            .putR("level", level)
            .putR("isReady", isReady.get())
            .putR("context", context.size())
            .putR("excludePatterns", excludePatterns != null ? excludePatterns.size() : 0)
            .putR("logFormatter", logFormatter.getClass().getSimpleName())
            .putR("MAX_LOG_NAME_LENGTH", MAX_LOG_NAME_LENGTH.get())
            .toJson();
    }
}
