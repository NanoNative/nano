> [Home](../../../README.md) / **[Concept](README.md)**

# Concept

Nano is a minimalist standalone library designed to facilitate the creation of microservices using plain, modern Java. 
Nano is a tool, not a framework, and it emphasizes simplicity, security, and efficiency.

### Modern and Fluent Design 🚀

Nano leverages fluent chaining and functional programming styles to create a syntax that resembles a stateless scripting
language. By avoiding annotations and other “black magic,” Nano maintains transparency and simplicity in its codebase.
Fluent and chaining means, there are no `get` and `set` prefixes and no `void` returns for methods.

### No External Dependencies 🔒

Nano is built without any foreign dependencies, ensuring a lean, secure library free from common vulnerabilities and
excessive dependencies. This results in a smaller, faster, and more secure codebase. You only need to trust and know the
license agreements of Nano.

### Minimal Resource Consumption 🌱

Nano is engineered for a minimal environmental footprint, utilizing fewer resources and making garbage collection more
efficient due to its functional programming style.

### Non-Blocking Virtual Threads 🧵

Nano utilizes non-blocking virtual threads from [Project Loom](https://jdk.java.net/loom/) to enhance efficiency and
performance. These threads maximize CPU utilization without blocking the main thread, eliminating the need for manual
thread limit settings.
Note that Nano cannot control Java’s built-in `ForkJoinPool` used for `java.util.concurrent` objects like streams.
To optimize performance, it is recommended to set the Java property to something like
this `-Djava.util.concurrent.ForkJoinPool.common.parallelism=100.` in case of high parallelism.

### GraalVM Compatibility ⚡

Nano is fully compatible with [GraalVM](https://www.graalvm.org), allowing you to compile native executables that do not
require a JVM to run. This feature is particularly useful in containerized and serverless environments.
Nano avoids reflection and dynamic class loading, ensuring seamless [GraalVM](https://www.graalvm.org) integration
without additional configuration.

### Extensible and Open 🪶

All Nano functions and classes are `public` or `protected`, allowing developers to extend or modify the library as
needed. This breaks the concept of immutable objects, but we think it's more important to be able to extend and modify
Nano than closing it. Means, every developer is responsible for the own code!
We still encourages contributions and improvements from the community.

### Modular Design 🧩

Nano’s [Event](../../events/README.md) system enables decoupling of functions, plugin
creation ([Services](../../services/README.md)), and function interception.
For example, you can globally control and respond to every error that occurs, similar to a global `Controller Advice`.
With that its also easy to change configurations on the fly.
This modular design allows services, such as the built-in [HttpService](../../services/httpservice/README.md) and
[MetricService](../../services/metricservice/README.md), to operate independently while still being able to interact
when started.

### Service-Based Architecture 📊

([Services](../../services/README.md)) in Nano function as plugins or extensions, executed only when explicitly added to
Nano programmatically.
This approach simplifies testing, as services and components can be tested independently without the need for mocking or
stubbing.
You execute only what you define, avoiding the pitfalls of auto-applying dependencies.

### Flexible Object Mapping 🔄

Nano’s built-in `TypeConverter` eliminates the need for custom objects by enabling easy conversion of `JSON`, `XML`, and
other simple Java objects.
For example, HTTP requests can be converted to `TypeInfo`, `TypeMap` or `TypeList`, which lazily convert fields to
the requested type. _See [TypeMap](https://github.com/YunaBraska/type-map) for more information._
If an object cannot be converted, it is straightforward to register a custom type conversion.
These [TypeMaps](https://github.com/YunaBraska/type-map) and TypeLists are used extensively, such as in events and the context.

### Configuration Management ⚙️

Nano uses a [Context](../../context/README.md) object to manage logging, tracing and configurations.
Nano reads property files and profiled properties which all end up in the [Context](../../context/README.md) Object.
The properties can be converted to the required types as needed.
This eliminates the need for custom configuration objects.




