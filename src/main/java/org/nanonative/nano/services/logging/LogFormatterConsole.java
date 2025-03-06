package org.nanonative.nano.services.logging;

import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.services.logging.model.LogLevel;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static org.nanonative.nano.services.logging.LogService.MAX_LOG_NAME_LENGTH;
import static org.nanonative.nano.services.logging.model.LogLevel.nanoLogLevelOf;

/**
 * Formatter for logging messages to the console.
 * <p>
 * This formatter provides a consistent log format comprising the timestamp, log level, logger name, and message.
 * The formatted log entries are easy to read and allow for quick scanning of log files. The log format is as follows:
 * <pre>
 * [Timestamp] [Log Level] [Logger Name] - Message
 * </pre>
 * <p>
 * The formatter supports parameterized messages, allowing insertion of values at runtime. To include dynamic content in your log messages,
 * use placeholders '{}' for automatic replacement or '%s' for manual specification. Each placeholder will be replaced with corresponding
 * parameters provided in the logging method call.
 * <p>
 * Usage Example:
 * <pre>
 * context.info(() -> throwable, "Processed records - success: [{}], failure: [%s], ignored; [{2}]", successCount, failureCount, ignoreCount);
 * </pre>
 * In this example, 'successCount' replaces the first '{}' placeholder, 'failureCount' replaces the '%s' placeholder and 'ignoreCount' replaces the last [{2}] placeholder.
 * </p>
 * <p>
 * Note: The formatter also handles exceptions by appending the stack trace to the log entry, should an exception be thrown during execution.
 * </p>
 */
public class LogFormatterConsole extends Formatter {
    protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    protected final int paddingLogLevel = Arrays.stream(LogLevel.values()).map(LogLevel::toString).mapToInt(String::length).max().orElse(5);

    /**
     * Format a LogRecord into a string representation.
     *
     * @param logRecord the log record to be formatted.
     * @return a formatted log string.
     */
    @SuppressWarnings("java:S3457")
    @Override
    public String format(final LogRecord logRecord) {
        final StringBuilder formattedLog = new StringBuilder();
        formattedLog.append("[")
                .append(dateFormat.format(new Date(logRecord.getMillis())))
                .append("] [")
                .append(String.format("%-" + paddingLogLevel + "s", nanoLogLevelOf(logRecord.getLevel())))
                .append("] [")
                .append(formatLoggerName(logRecord))
                .append("] - ")
                .append(applyCustomFormat(formatMessage(logRecord), logRecord.getParameters()))
                .append(NanoUtils.LINE_SEPARATOR);
        if (logRecord.getThrown() != null) {
            formattedLog.append(convertObj(logRecord.getThrown(), String.class)).append(NanoUtils.LINE_SEPARATOR);
        }
        return formattedLog.toString();
    }

    @SuppressWarnings("java:S3457")
    protected static String formatLoggerName(final LogRecord logRecord) {
        final int dot = logRecord.getLoggerName().lastIndexOf(".");
        return String.format("%-" + MAX_LOG_NAME_LENGTH.get() + "s", (dot != -1 ? logRecord.getLoggerName().substring(dot + 1) : logRecord.getLoggerName()));
    }

    /**
     * Replacing placeholders with parameters.
     *
     * @param message the message to be formatted.
     * @param params  the parameters for the message.
     * @return a string with the formatted message.
     */
    protected static String applyCustomFormat(final String message, final Object... params) {
        if (message != null && params != null && params.length > 0) {
            final String result = message.replace("{}", "%s");
            return String.format(result, params);
        }
        return message;
    }
}
