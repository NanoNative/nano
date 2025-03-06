> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**Logger**](README.md)

# LogService

The [LogService](../logger/README.md) is a simple wrapper around the build in `System.out.print` which comes with predefined log formats `console`
and `json`. The javaLogger is not work concurrently. But `Level`, `LogRecord` and `Formatter` are supported.
This service is starting automatically if no other LogService is provided.

## Placeholder

The logger supports placeholders in the message string. The placeholders are replaced by the arguments passed to the
logger.

* `{}` and `%s` is replaced by the argument at the same index
* `{0}` is replaced by the argument at the specified index

## Log Formatter

The [LogService](../logger/README.md) supports two log formatters at default:

* `console` - The console formatter logs the message to the console.
    * Example: `context.info(() -> "Hello {}", "World")`
    * Output: `[2024-11-11 11:11:11.111] [DEBUG] [Nano] - Hello World`
* `json` - The json formatter logs the message as json to the console.
    * Example: `context.debug(() -> "Hello {}", "World")`
      Output:
      `{"Hello":"World", "level":"DEBUG","logger":"Nano","message":"Hello World","timestamp":"2024-11-11 11:11:11.111"}`

## Custom Log Formatter

Custom log formatters can be registered by using `LogFormatRegister.registerLogFormatter(Name, Formatter)` - (
java.util.logging.Formatter)

## Custom LogService

The default Logger can be overwritten by providing a custom `Service` which extends the `LogService` e.g.
`new Nano(new CustomLogger())` 
