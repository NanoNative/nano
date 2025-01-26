package org.nanonative.nano.helper.logger.logic;

import org.nanonative.nano.helper.logger.model.LogErrorHandler;
import org.nanonative.nano.helper.logger.model.LogInfoHandler;
import org.nanonative.nano.helper.logger.model.LogLevel;
import berlin.yuna.typemap.model.TypeMap;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.nanonative.nano.core.model.Context.CONFIG_LOG_LEVEL;
import static org.nanonative.nano.core.model.Context.CONFIG_LOG_FORMATTER;
import static org.nanonative.nano.core.model.Context.CONTEXT_LOG_QUEUE_KEY;

@SuppressWarnings({"unused", "UnusedReturnValue", "java:S1104"})
public class NanoLogger {

    //TODO: Logger: change format on runtime
    //TODO: Logger exclude package pattern config
    //TODO: extract logger as a service

    public static final Formatter DEFAULT_LOG_FORMATTER = new LogFormatterJson();
    public static final LogInfoHandler DEFAULT_LOG_INFO_HANDLER = new LogInfoHandler(DEFAULT_LOG_FORMATTER);
    public static final LogErrorHandler DEFAULT_LOG_ERROR_HANDLER = new LogErrorHandler(DEFAULT_LOG_FORMATTER);
    protected final Logger javaLogger;
    // cause the log level of java is not thread safe
    // FIXME: create very own logger as every config of the java logger is not thread safe
    protected final AtomicReference<LogLevel> level = new AtomicReference<>(LogLevel.DEBUG);
    protected LogQueue logQueue;
    public static final AtomicInteger MAX_LOG_NAME_LENGTH = new AtomicInteger(10);

    public NanoLogger(final Object object) {
        this(object.getClass());
    }

    public NanoLogger(final Class<?> clazz) {
        javaLogger = Logger.getLogger(clazz.getName());
        javaLogger.setUseParentHandlers(false);
        javaLogger.setLevel(Level.ALL);
        addHandlerIfAbsent(DEFAULT_LOG_INFO_HANDLER);
        addHandlerIfAbsent(DEFAULT_LOG_ERROR_HANDLER);
        MAX_LOG_NAME_LENGTH.updateAndGet(length -> Math.max(length, clazz.getSimpleName().length()));
    }

    public Logger javaLogger() {
        return javaLogger;
    }

    public LogQueue logQueue() {
        return logQueue;
    }

    public NanoLogger logQueue(final LogQueue logQueue) {
        this.logQueue = logQueue;
        return this;
    }

    public Formatter formatter() {
        return javaLogger.getHandlers().length > 0 ? javaLogger.getHandlers()[0].getFormatter() : null;
    }

    public NanoLogger formatter(final Formatter formatter) {
        for (final Handler handler : javaLogger.getHandlers()) {
            handler.setFormatter(formatter);
        }
        return this;
    }

    public NanoLogger level(final LogLevel level) {
        this.level.set(level);
        return this;
    }

    public LogLevel level() {
        return level.get();
    }

    public NanoLogger fatal(final Supplier<String> message, final Object... params) {
        return log(LogLevel.FATAL, null, message, params);
    }

    public NanoLogger fatal(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.FATAL, thrown, message, params);
    }

    public NanoLogger error(final Supplier<String> message, final Object... params) {
        return log(LogLevel.ERROR, null, message, params);
    }

    public NanoLogger error(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.ERROR, thrown, message, params);
    }

    public NanoLogger warn(final Supplier<String> message, final Object... params) {
        return log(LogLevel.WARN, null, message, params);
    }

    public NanoLogger warn(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.WARN, thrown, message, params);
    }

    public NanoLogger info(final Supplier<String> message, final Object... params) {
        return log(LogLevel.INFO, null, message, params);
    }

    public NanoLogger info(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.INFO, thrown, message, params);
    }

    public NanoLogger debug(final Supplier<String> message, final Object... params) {
        return log(LogLevel.DEBUG, null, message, params);
    }

    public NanoLogger debug(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.DEBUG, thrown, message, params);
    }

    public NanoLogger trace(final Supplier<String> message, final Object... params) {
        return log(LogLevel.TRACE, null, message, params);
    }

    public NanoLogger trace(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.TRACE, thrown, message, params);
    }

    public NanoLogger log(final LogLevel level, final Supplier<String> message, final Object... params) {
        return log(level, null, message, params);
    }

    public NanoLogger log(final LogLevel level, final Throwable thrown, final Supplier<String> message, final Object... params) {
        if (level != null && message != null && isLoggable(level)) {
            final LogRecord logRecord = new LogRecord(level.toJavaLogLevel(), message.get());
            logRecord.setParameters(params);
            logRecord.setThrown(thrown);
            logRecord.setLoggerName(javaLogger.getName());
            if (logQueue == null || !logQueue.log(javaLogger, logRecord)) {
                javaLogger.log(logRecord);
            }
        }
        return this;
    }

    public NanoLogger configure(final TypeMap config) {
        config.asOpt(LogLevel.class, CONFIG_LOG_LEVEL).ifPresent(this::level);
        config.asOpt(LogQueue.class, CONTEXT_LOG_QUEUE_KEY).ifPresent(this::logQueue);
        config.asOpt(Formatter.class, CONFIG_LOG_FORMATTER).ifPresent(this::formatter);
        return this;
    }

    protected boolean isLoggable(final LogLevel level) {
        final int levelValue = this.level.get().ordinal();
        return level.ordinal() <= levelValue;
    }

    protected void addHandlerIfAbsent(final Handler newHandler) {
        for (final Handler existingHandler : javaLogger.getHandlers()) {
            if (existingHandler.getClass().equals(newHandler.getClass())) {
                return;
            }
        }
        javaLogger.addHandler(newHandler);
    }
}
