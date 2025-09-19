package org.nanonative.nano.helper;

import java.util.function.Consumer;

@FunctionalInterface
public interface ExFunction<T, R> {

    @SuppressWarnings({"java:S112", "RedundantThrows"})
    R apply(T t) throws Exception;

    default R apply(final T t, final Consumer<Exception> onError) {
        try {
            return this.apply(t);
        } catch (final Exception e) {
            onError.accept(e);
            return null;
        }
    }
}
