package org.nanonative.nano.helper.logger;

import org.nanonative.nano.helper.logger.logic.LogFormatterConsole;
import org.nanonative.nano.helper.logger.logic.LogFormatterJson;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Formatter;

@SuppressWarnings("unused")
public class LogFormatRegister {

    @SuppressWarnings("java:S2386")
    public static final Map<String, Formatter> LOG_FORMATTERS = new ConcurrentHashMap<>();

    public static void registerLogFormatter(final String id, final Formatter formatter) {
        LOG_FORMATTERS.put(id, formatter);
    }

    public static Formatter getLogFormatter(final String id) {
        if("console".equals(id)){
            return getLogFormatter(id, LogFormatterConsole::new);
        } else if ("json".equals(id)){
            return getLogFormatter(id, LogFormatterJson::new);
        } else {
            return getLogFormatter(id, LogFormatterConsole::new);
        }
    }

    public static Formatter getLogFormatter(final String id, final Supplier<Formatter> orRegister) {
        return LOG_FORMATTERS.computeIfAbsent(id, formatter -> orRegister.get());
    }

    private LogFormatRegister() {
        // static util class
    }
}
