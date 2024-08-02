> [Home](../../README.md) / **[Registers](README.md)**

# Registers

Nano comes with a set of registers that are used to add custom functionality to internal components.
It's recommended to use the register in `static` blocks to ensure that they are only executed on need. Like when the
class is used.

### ConfigRegister

The `ConfigRegister` is used to register custom configuration values. This register is non functional and mostly used
for documentation purposes like the help menu. The config keys are usually separated by `_` and written in lowercase.
This ensures a common naming convention which is compatible in environments like env variables and as parameters.

**Usage:**

```java
static {
    // Register a config key
    String key = ConfigRegister.registerConfig("my_config_key", "my description");

    // Getting a config description
    String description = ConfigRegister.configDescriptionOf("my_config_key");

    // Getting all configs
    Map<String, String> allConfigs = CONFIG_KEYS;
}
```

### EventChannelRegister

The `EventChannelRegister` is used to register custom [Event](../events/README.md) channels to send or
subscribe [events](../events/README.md) to.
the registration is needed to create unique channel ids for the [Event](../events/README.md) bus. These ids are faster
than using `String` ids

**Usage:**

```java
static {
    // Register a channel
    int MY_EVENT_CHANNEL_ID = EventChannelRegister.registerChannelId("my_channel_name");

    // Getting a channel name by id
    String myChanelName = EventChannelRegister.eventNameOf(MY_EVENT_CHANNEL_ID);

    // Getting a channelId by name
    int MY_EVENT_CHANNEL_ID = EventChannelRegister.eventIdOf("my_channel_name");

    // checking if a channel is registered
    boolean isChannelAvailable = ConfigRegister.isChannelIdAvailable("my_config_key");
}
```

### LogFormatRegister

This register is used to register custom log formats. Default formats are `console` and `json`.
The [Logger](../logger/README.md) is still under construction. The functionality might change in the future.
Simply use the default log Formatter interface of java `java.util.logging.Formatter`.

**Usage:**

```java
static {
    // Register a log formatter
    LogFormatRegister.registerLogFormatter("xml", new XmlLogFormatter());

    // Getting a log formatter by name
    Formatter jsonFormatter = LogFormatRegister.getLogFormatter("json");
}
```

### TypeConversionRegister

The `TypeConversionRegister` is used to register custom type converters. It's the core of Nano.
These type conversion are used in the [Config/Context](../context/README.md), [Event](../events/README.md)
Cache, [HttpService](../services/httpservice/README.md) request & responses and everything which
uses `TypeMap`, `TypeList` or `TypeInfo`. _See [TypeMap](https://github.com/YunaBraska/type-map) for more information._

**Usage:**

```java
import berlin.yuna.typemap.logic.TypeConverter;

static {
    // Register type conversion from String to LogLevel
    registerTypeConvert(String.class, LogLevel.class, LogLevel::nanoLogLevelOf);

    // Register type conversion from LogLevel to String
    registerTypeConvert(LogLevel.class, String.class, Enum::name);

    // Manual type conversion
    TypeConverter.convertObj("INFO", LogLevel.class);
}
```
