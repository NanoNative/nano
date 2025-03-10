package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.TypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static java.util.Arrays.stream;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.services.metric.logic.MetricService.EVENT_METRIC_UPDATE;
import static org.nanonative.nano.services.metric.model.MetricType.GAUGE;

/**
 * Abstract base class for all services in the Nano framework.
 * Provides core functionality for service lifecycle management, event handling, and configuration.
 * Services can implement only the methods they need - all methods are optional and have safe default behaviors.
 * This flexibility allows for minimal service implementations while still providing a robust framework
 * for more complex services when needed.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class Service {

    protected final long createdAtMs;
    protected final AtomicBoolean isReady = new AtomicBoolean(false);
    protected Context context;

    /**
     * Creates a new Service instance and records its creation timestamp.
     * The timestamp can be used for service uptime tracking and performance metrics.
     */
    protected Service() {
        this.createdAtMs = System.currentTimeMillis();
    }

    /**
     * Starts the service. This method is called during service initialization.
     * Optional implementation - services can leave this empty if no startup logic is needed.
     * Common uses include:
     * - Initializing resources
     * - Setting up connections
     * - Starting background tasks
     * See also {@link Service#configure(TypeMapI, TypeMapI)} for configuration setup.
     */
    public abstract void start();

    /**
     * Stops the service gracefully. This method is called during service shutdown.
     * Optional implementation - services can leave this empty if no cleanup is needed.
     * Common uses include:
     * - Closing connections
     * - Releasing resources
     * - Stopping background tasks
     */
    public abstract void stop();

    /**
     * Handles service failures and errors.
     * Optional implementation - services can return null if no specific error handling is needed.
     * Null means the error will be logged automatically if no other listener or service handles it.
     * Useful for:
     * - Custom error recovery strategies
     * - Error logging
     * - Notifying other components of failures
     *
     * @param error The error event to handle
     * @return Response object from error handling, can be null
     */
    public abstract Object onFailure(final Event error);

    /**
     * Processes incoming events for the service.
     * Optional implementation - services can leave this empty if they don't need to handle events.
     * Useful for:
     * - Responding to system events
     * - Inter-service communication
     * - State updates based on external triggers
     *
     * @param event The event to process
     */
    public abstract void onEvent(final Event event);

    /**
     * Configures the service with the provided configuration.
     * This is a convenience method that calls configure(config, config).
     * Optional override - default implementation handles basic configuration needs.
     *
     * @param config The configuration to apply
     */
    public void configure(final TypeMapI<?> config) {
        configure(config, config);
    }

    /**
     * Configures the service with changes while maintaining merged state.
     * Optional implementation - services can leave this empty if no configuration is needed.
     * Useful for:
     * - Service initialization with configuration
     * - Handling dynamic configuration updates
     * - Managing service state
     * - Applying configuration changes without service restart
     *
     * @param changes The new configuration changes to apply
     * @param merged  The complete merged configuration state - this will be represented in {@link Service#context} after the method is done
     */
    public abstract void configure(final TypeMapI<?> changes, final TypeMapI<?> merged);

    /**
     * Returns the simple name of the service class.
     * This method provides a default naming convention for services.
     * Can be overridden if a custom naming scheme is needed.
     *
     * @return Service name derived from class name
     */
    public String name() {
        return this.getClass().getSimpleName();
    }

    /**
     * Gets the current context of the service.
     * The context provides access to the service's runtime environment and shared resources.
     * Services typically don't need to override this method.
     *
     * @return The service context
     */
    public Context context() {
        return context;
    }

    /**
     * Checks if the service is ready to handle requests.
     * Used by the framework to determine if the service has completed initialization.
     * Services typically don't need to override this method.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isReady() {
        return isReady.get();
    }

    /**
     * Gets the ready state of the service as an AtomicBoolean.
     * Provides thread-safe access to the service's ready state.
     * Services typically don't need to override this method.
     *
     * @return AtomicBoolean representing the ready state
     */
    public AtomicBoolean isReadyState() {
        return isReady;
    }

    /**
     * Sets the context for this service.
     * Called by the framework during service initialization.
     * Services typically don't need to override this method.
     *
     * @param context The context to set
     * @return This service instance for method chaining
     */
    public Service context(final Context context) {
        this.context = context;
        return this;
    }

    /**
     * Gets the creation timestamp of the service.
     * Useful for monitoring and debugging purposes.
     * Services typically don't need to override this method.
     *
     * @return Creation timestamp in milliseconds since epoch
     */
    public long createdAtMs() {
        return createdAtMs;
    }

    //########## GLOBAL SERVICE METHODS ##########

    /**
     * Processes received events and handles configuration changes.
     * This is a core framework method that:
     * - Handles configuration update events
     * - Manages configuration merging
     * - Delegates other events to onEvent()
     * Services typically don't need to override this method.
     *
     * @param event The event to process
     * @return This service instance for method chaining
     */
    public Service receiveEvent(final Event event) {
        if (event.channelId() == EVENT_CONFIG_CHANGE) {
            event.payloadOpt().filter(TypeMapI.class::isInstance).map(TypeMapI.class::cast)
                .or(() -> event.payloadOpt(Map.class).map(TypeMap::new).map(TypeMapI.class::cast))
                .ifPresentOrElse(configs -> {
                    final TypeMap merged = new TypeMap(context);
                    context.forEach(merged::putIfAbsent);
                    configure(configs, merged);
                    context.putAll(configs);
                }, () -> onEvent(event));
        } else {
            onEvent(event);
        }
        return this;
    }

    /**
     * Creates and configures a new NanoThread for this service.
     * This is a framework method that handles:
     * - Service initialization in a separate thread
     * - Context setup
     * - Service startup sequence
     * - Ready state management
     * - Error handling
     * Services typically don't need to override this method.
     *
     * @param context The context for the thread
     * @return Configured NanoThread instance
     */
    public NanoThread nanoThread(final Context context) {
        return new NanoThread().run(() -> context.nano() != null ? context : null, () -> {
            final long startTime = System.currentTimeMillis();
            if (!isReady.get()) {
                this.context = context.newContext(this.getClass());
                this.configure(context);
                this.start();
                this.context.broadcastEvent(EVENT_APP_SERVICE_REGISTER, () -> this);
                this.context.sendEvent(EVENT_METRIC_UPDATE, () -> new MetricUpdate(GAUGE, "application.services.ready.time", System.currentTimeMillis() - startTime, Map.of("class", this.getClass().getSimpleName())), result -> {});
                isReady.set(true);
            }
        }).onComplete((nanoThread, error) -> {
            if (error != null)
                this.context.sendEventError(context.newEvent(EVENT_APP_SERVICE_REGISTER).payload(() -> this), this, error);
        });
    }

    /**
     * Creates NanoThreads for multiple services.
     * A utility method that simplifies bulk service thread creation.
     * Useful for:
     * - Starting multiple services in parallel
     * - Managing service groups
     * - Orchestrating service startup
     *
     * @param context  The context for the threads
     * @param services Array of services to create threads for
     * @return Array of configured NanoThreads
     */
    public static NanoThread[] threadsOf(final Context context, final Service... services) {
        return stream(services).map(service -> service.nanoThread(context)).toArray(NanoThread[]::new);
    }
}
