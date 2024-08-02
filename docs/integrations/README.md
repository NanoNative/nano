> [Home](../../README.md) / [**Integrations**](README.md)
 
[Spring Boot](#-nano-in-spring-boot)
| [Micronaut](#-nano-in-micronaut)
| [Quarkus](#-nano-in-quarkus).

# Integrations

Nano is fully standalone and can be integrated into various frameworks and libraries.
This section provides examples of how to integrate Nano into

## 🌱 Nano in Spring boot

* Run Nano as Bean

```java

@Configuration
public class NanoConfiguration {

    @Bean
    public Nano nanoInstance() {
        // Initialize your Nano instance with the desired services
        return new Nano(); // Optionally add your services and configurations here
    }
}
```

* Use Nano in a Service

```java

@Service
public class SomeService {

    private final Nano nano;

    @Autowired
    public SomeService(final Nano nano) {
        this.nano = nano;
        // Use Nano instance as needed
    }
}
```

Nano has a graceful shutdown by itself, but it could be useful to trigger it from a Spring bean.

* Graceful shutdown using `DisposableBean`

```java

@Component
public class NanoManager implements DisposableBean {

    private final Nano nano;

    public NanoManager(final Nano nano) {
        this.nano = nano;
    }

    @Override
    public void destroy() {
        nano.stop(); // Trigger Nano's shutdown process
    }
}
```

* Graceful shutdown using `@PreDestroy` annotation

```java

@Component
public class NanoManager {

    private final Nano nano;

    public NanoManager(final Nano nano) {
        this.nano = nano;
    }

    @PreDestroy
    public void onDestroy() {
        nano.stop(); // Trigger Nano's shutdown process
    }
}
```

## 🧑‍🚀 Nano in Micronaut

* Define the Nano Bean

```java

@Factory
public class NanoFactory {

    @Singleton
    public Nano nanoInstance() {
        // Initialize your Nano instance with desired services
        return new Nano(); // Optionally add services and configurations here
    }
}
```

* Use Nano in Your Application

```java

@Singleton
public class SomeService {

    private final Nano nano;

    public SomeService(final Nano nano) {
        this.nano = nano;
        // Use the Nano instance as needed
    }
}
```

* Graceful shutdown using `@ServerShutdownEvent`

```java

@Singleton
public class NanoManager implements ApplicationEventListener<ServerShutdownEvent> {

    private final Nano nano;

    public NanoManager(final Nano nano) {
        this.nano = nano;
    }

    @Override
    public void onApplicationEvent(final ServerShutdownEvent event) {
        nano.stop(); // Trigger Nano's shutdown process
    }
}
```

## 🐸 Nano in Quarkus

* Define the Nano Producer

```java

@ApplicationScoped
public class NanoProducer {

    @Produces
    public Nano produceNano() {
        // Initialize your Nano instance with the desired services
        return new Nano(); // Optionally add your services and configurations here
    }
}
```
