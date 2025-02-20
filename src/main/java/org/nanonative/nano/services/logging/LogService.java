package org.nanonative.nano.services.logging;

import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.config.ConfigRegister;
import org.nanonative.nano.helper.event.EventChannelRegister;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.helper.logger.LogFormatRegister;
import org.nanonative.nano.helper.logger.logic.LogFormatterConsole;
import org.nanonative.nano.helper.logger.model.LogLevel;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;

public class LogService extends Service {

    // CONFIG
    public static final String CONFIG_LOG_LEVEL = ConfigRegister.registerConfig("app_log_level", "Log level for the application (see " + LogLevel.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_FORMATTER = ConfigRegister.registerConfig("app_log_formatter", "Log formatter (see " + LogFormatRegister.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_EXCLUDE_PATTERNS = ConfigRegister.registerConfig("app_log_excludes", "Exclude patterns for logger names");

    // CHANNEL
    public static final int EVENT_LOGGING = EventChannelRegister.registerChannelId("EVENT_LOGGING");

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
        event.ifPresentAck(EVENT_CONFIG_CHANGE, TypeMap.class, config -> {
            config.asOpt(LogLevel.class, CONFIG_LOG_LEVEL).map(LogLevel::toJavaLogLevel).ifPresent(this::level);
            config.asOpt(Formatter.class, CONFIG_LOG_FORMATTER).ifPresent(this::formatter);
            config.asStringOpt(CONFIG_LOG_EXCLUDE_PATTERNS).map(patterns -> Arrays.stream(patterns.split(",")).map(String::trim).toList()).ifPresent(patterns -> excludePatterns = patterns);
        });
        event.ifPresentAck(EVENT_LOGGING, LogRecord.class, this::log);
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

    public void log(final LogRecord logRecord) {
        if (isLoggable(logRecord)) {
            context.run(() -> {
                final String formattedMessage = logFormatter.format(logRecord);
                if (logRecord.getLevel().intValue() < Level.WARNING.intValue()) {
                    System.out.println(formattedMessage);
                } else {
                    System.err.println(formattedMessage);
                }
            });
        }
    }

    private boolean isLoggable(final LogRecord logRecord) {
        if (logRecord.getLevel().intValue() > level.intValue()) return false;
        return excludePatterns == null || excludePatterns.stream().noneMatch(exclusion -> logRecord.getLoggerName().contains(exclusion));
    }
}
