package org.nanonative.nano.core;

import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.ExRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
 * The abstract base class for {@link Nano} framework providing {@link Service} handling functionalities.
 *
 * @param <T> The type of the {@link NanoServices} implementation, used for method chaining.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public abstract class NanoServices<T extends NanoServices<T>> extends NanoThreads<T> {

    protected final List<Service> services;

    /**
     * Initializes {@link NanoServices} with configurations and command-line arguments.
     *
     * @param config Configuration parameters for the {@link NanoServices} instance.
     * @param args   Command-line arguments passed during the application start.
     */
    protected NanoServices(final Map<Object, Object> config, final String... args) {
        super(config, args);
        this.services = new CopyOnWriteArrayList<>();
        subscribeEvent(Context.EVENT_APP_SERVICE_REGISTER, event -> event.payloadOpt(Service.class).map(this::registerService).ifPresent(nano -> event.acknowledge()));
        subscribeEvent(Context.EVENT_APP_SERVICE_UNREGISTER, event -> event.payloadOpt(Service.class).map(service -> unregisterService(event.context(), service)).ifPresent(nano -> event.acknowledge()));
    }

    /**
     * Retrieves a {@link Service} of a specified type.
     *
     * @param <S>          The type of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the {@link Service} to retrieve.
     * @return The first instance of the specified {@link Service}, or null if not found.
     */
    public <S extends Service> S service(final Class<S> serviceClass) {
        final List<S> results = services(serviceClass);
        if (results != null && !results.isEmpty()) {
            return results.getFirst();
        }
        return null;
    }

    /**
     * Retrieves a list of services of a specified type.
     *
     * @param <S>          The type of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the service to retrieve.
     * @return A list of services of the specified type. If no services of this type are found,
     * an empty list is returned.
     */
    public <S extends Service> List<S> services(final Class<S> serviceClass) {
        if (serviceClass != null) {
            return services.stream()
                .filter(serviceClass::isInstance)
                .map(serviceClass::cast)
                .toList();
        }
        return emptyList();
    }

    /**
     * Provides an unmodifiable list of all registered {@link Service}.
     *
     * @return An unmodifiable list of {@link Service} instances.
     */
    public List<Service> services() {
        return unmodifiableList(services);
    }

    /**
     * Shuts down all registered {@link Service} gracefully.
     *
     * @param context The {@link Context} in which the services are shut down.
     */
    protected void shutdownServices(final Context context) {
        if (context.asBooleanOpt(Context.CONFIG_PARALLEL_SHUTDOWN).orElse(false)) {
            try {
                context.runAwait(services.stream().map(service -> (ExRunnable) () -> unregisterService(context, service)).toArray(ExRunnable[]::new));
            } catch (final Exception err) {
                context.fatal(err, () -> "Service [{}] shutdown error. Looks like the Death Star blew up again.", Service.class.getSimpleName());
                Thread.currentThread().interrupt();
            }
        } else {
            new ArrayList<>(services).reversed().forEach(service -> unregisterService(context, service));
        }
    }

    /**
     * Registers a new service in the {@link Nano} framework.
     *
     * @param service The {@link Service} to register.
     * @return Self for chaining
     */
    @SuppressWarnings("unchecked")
    protected T registerService(final Service service) {
        if (service != null) {
            services.add(service);
        }
        return (T) this;
    }

    /**
     * Unregisters a {@link Service} from the {@link Nano} framework and stops it.
     *
     * @param context The {@link Context} in which the {@link Service} is unregistered and stopped.
     * @param service The {@link Service} to unregister and stop.
     * @return Self for chaining
     */
    @SuppressWarnings("unchecked")
    protected T unregisterService(final Context context, final Service service) {
        if (service != null) {
            services.remove(service);
            try {
                if (service.isReadyState().compareAndSet(true, false))
                    service.stop();
            } catch (final Exception e) {
                context.warn(e, () -> "Stop [{}] error. Somebody call the Ghostbusters!", service.name());
            }
        }
        return (T) this;
    }
}
