package org.nanonative.nano.services.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static org.nanonative.nano.helper.NanoUtils.LINE_SEPARATOR;
import static org.nanonative.nano.services.logging.model.LogLevel.nanoLogLevelOf;

/**
 * A log formatter that outputs log records in JSON format.
 * <p>
 * This formatter structures log messages into JSON objects, which is beneficial for systems that ingest log data for analysis,
 * allowing for easy parsing and structured querying of log data.
 * <p>
 * Usage Example:
 * <pre>
 * context.info(() -> throwable, "Processed records - success: [{}], failure: [%s], ignored; [{2}]", successCount, failureCount, ignoreCount, Map.of("username", "yuna"));
 * </pre>
 * In this example, 'successCount' replaces the first '{}' placeholder, 'failureCount' replaces the '%s' placeholder and 'ignoreCount' replaces the last [{2}] placeholder.
 * The formatter will convert the log into a JSON line.
 * The map's keys and values becoming part of the JSON structure.
 * A key value map is not really needed as the keys and values from the messages itself becomes a part of the JSON structure as well. This makes it easier to switch between console and json logging.
 * </p>
 * <p>
 * Additionally, it supports automatic key-value extraction from the log message itself, enabling inline parameterization.
 * The extracted keys and values are also included in the JSON output.
 * </p>
 * <p>
 * The formatter handles exceptions by appending a "error" field with the exception message to the JSON log entry.
 * </p>
 */
public class LogFormatterJson extends Formatter {
    protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    protected static final Pattern MESSAGE_KEY_VALUE_PATTERN = Pattern.compile("(\\w+):?\\s*\\[\\{\\}]");

    /**
     * Formats a log record into a JSON string.
     *
     * @param logRecord The log record to format.
     * @return The log record formatted as a JSON string.
     */
    @Override
    public String format(final LogRecord logRecord) {
        final Object[] params = logRecord.getParameters();
        final Map<String, String> jsonMap = new TreeMap<>();
        final String message = LogFormatterConsole.applyCustomFormat(logRecord.getMessage(), params);
        final int dot = logRecord.getLoggerName().lastIndexOf(".");
        extractKeyValuesFromMessage(jsonMap, logRecord.getMessage(), params);
        addJsonEntries(jsonMap, params);

        putEntry(jsonMap, "message", message);
        putEntry(jsonMap, "timestamp", dateFormat.format(new Date(logRecord.getMillis())));
        putEntry(jsonMap, "level", nanoLogLevelOf(logRecord.getLevel()));
        putEntry(jsonMap, "package", dot != -1 ? logRecord.getLoggerName().substring(0, dot) : "");
        putEntry(jsonMap, "logger", dot != -1 ? logRecord.getLoggerName().substring(dot + 1) : logRecord.getLoggerName());
        if (logRecord.getThrown() != null) {
            putEntry(jsonMap, "error", jsonEscape(convertObj(logRecord.getThrown(), String.class)));
        }
        return "{" + jsonMap.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",")) + "}" + LINE_SEPARATOR;
    }

    /**
     * Extracts key-value pairs from the message and stores them in a map.
     *
     * @param jsonMap The map to store the key-value pairs.
     * @param message The log message.
     * @param params  The parameters for the log message.
     */
    protected void extractKeyValuesFromMessage(final Map<String, String> jsonMap, final String message, final Object[] params) {
        final Matcher matcher = MESSAGE_KEY_VALUE_PATTERN.matcher(message);

        int index = 0;
        while (matcher.find() && params != null && index < params.length) {
            putEntry(jsonMap, matcher.group(1), params[index]);
            index++;
        }
    }

    /**
     * Adds additional key-value pairs to the map.
     *
     * @param jsonMap The map to store the key-value pairs.
     * @param params  The parameters for the log message.
     */
    protected void addJsonEntries(final Map<String, String> jsonMap, final Object[] params) {
        if (params != null) {
            for (final Object param : params) {
                if (param instanceof final Map<?, ?> map) {
                    for (final Map.Entry<?, ?> entry : map.entrySet()) {
                        putEntry(jsonMap, entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * Adds escaped and converted key-value pairs to the map.
     *
     * @param jsonMap The map to store the key-value pairs.
     * @param key     key
     * @param value   value
     */
    protected void putEntry(final Map<String, String> jsonMap, final Object key, final Object value) {
        jsonMap.put(jsonEscape(convertObj(key, String.class)), jsonEscape(convertObj(value, String.class)));
    }

    /**
     * Escapes special characters for JSON compatibility.
     *
     * @param value The object to escape.
     * @return The escaped string.
     */
    protected String jsonEscape(final Object value) {
        if (value == null) return null;
        final String strValue = value.toString();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strValue.length(); i++) {
            final char c = strValue.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
