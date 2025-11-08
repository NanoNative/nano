> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**MetricService**](README.md)

* [Usage](#usage)
  * [Start Metric Service](#start-metric-service)
  * [Create Custom Metrics](#create-custom-metrics)
* [Configuration](#configuration)
* [Events](#events)

# Metric Service

Is a default [Services](../../services/README.md) of Nano which is responsible for collecting metrics.
This service solves only basic metrics.
Currently, there is no mechanism to push metrics to other applications.
The standard and best practice is to use a dedicated metric collector like Prometheus and poll for metrics.
Same as for tracking network traffic.
This way ensures, that microservice will stay small, simple without putting unnecessary complexity into it.
However, it is possible to extend or wrap the service with a custom implementation. E.g. with
a [Scheduler](../../schedulers/README.md) and the [HttpClient](../httpserver/README.md#send-http-requests).
The [MetricService](README.md) provides simple methods to get the metrics in the format
of `Influx`, `Dynamo`, `Wavefront` and `Prometheus`.

## Usage

### Start Metric Service

A) As startup [Service](../../services/README.md): `new Nano(new MetricService())`

B) Contextual `context.run(new MetricService())` - this way its possible to provide a custom configuration.

### Metric Endpoints

To get the metrics via HTTP, its necessary to also start
the [HttpServer](../httpserver/README.md) `new Nano(new MetricService(), new HttpServer())`.

The following endpoints are available:

* `/metrics/influx`
* `/metrics/dynamo`
* `/metrics/wavefront`
* `/metrics/prometheus`

### Create Custom Metrics

```java
public static void main(final String[] args) {
    final Context context = new Nano(args, new MetricService(), new HttpServer()).context(MyClass.class);

    // create counter
    context.newEvent(EVENT_METRIC_UPDATE).payload(() -> new MetricUpdate(COUNTER, "my.counter.key", 130624, metricTags)).send();
    // create gauge
    context.newEvent(EVENT_METRIC_UPDATE).payload(() -> new MetricUpdate(GAUGE, "my.gauge.key", 200888, metricTags)).send();
    // start timer
    context.newEvent(EVENT_METRIC_UPDATE).payload(() -> new MetricUpdate(TIMER_START, "my.timer.key", null, metricTags)).send();
    // end timer
    context.newEvent(EVENT_METRIC_UPDATE).payload(() -> new MetricUpdate(TIMER_END, "my.timer.key", null, metricTags)).send();
}
```

## Configuration

| [Config](../../context/README.md#configuration) | Type     | Default               | Description                        |
|-------------------------------------------------|----------|-----------------------|------------------------------------|
| `app_service_metrics_base_url`                  | `String` | `/metrics`            | Base path for all metric endpoints |
| `app_service_influx_metrics_url`                | `String` | `/metrics/influx`     | Custom path for Influx             |
| `app_service_dynamo_metrics_url`                | `String` | `/metrics/dynamo`     | Custom path for Dynamo             |
| `app_service_prometheus_metrics_url`            | `String` | `/metrics/prometheus` | Custom path for prometheus         |
| `app_service_wavefront_metrics_url`             | `String` | `/metrics/wavefront`  | Custom path for Wavefront          |

## Events

| In 🔲 <br/> Out 🔳 | [Event](../../events/README.md) | Payload        | Response     | Description                                |
|--------------------|---------------------------------|----------------|--------------|--------------------------------------------|
| 🔲                 | `EVENT_METRIC_UPDATE`           | `MetricUpdate` | `true`       | Sets or updates specific metric            |
| 🔲                 | `EVENT_APP_HEARTBEAT`           | `-`            | `true`       | (Internal usage) Updates system metrics    |
| 🔲                 | `EVENT_HTTP_REQUEST`            | `HttpObject`   | `HttpObject` | (Internal usage) provides metric endpoints |
