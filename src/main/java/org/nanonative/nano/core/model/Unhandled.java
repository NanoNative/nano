package org.nanonative.nano.core.model;

import org.nanonative.nano.core.Nano;

import java.util.Optional;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;

public record Unhandled(Context context, Object payload, Throwable exception) {

    public Nano nano() {
        return context == null ? null : context.nano();
    }

    public <T> Optional<T> payloadOpt(final Class<T> type) {
        return Optional.ofNullable(payload(type));
    }

    public <T> T payload(final Class<T> type) {
        return convertObj(payload, type);
    }

    public Throwable exception() {
        return exception;
    }

    @Override
    public String toString() {
        return "Unhandled{" +
            "payload=" + payload +
            ", exception=" + exception +
            '}';
    }
}
