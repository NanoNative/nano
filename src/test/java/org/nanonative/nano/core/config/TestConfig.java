package org.nanonative.nano.core.config;

import org.nanonative.nano.services.logging.model.LogLevel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.nanonative.nano.helper.NanoUtils.tryExecute;

public class TestConfig {

    /**
     * Defines the log level for testing purposes, allowing for easy adjustment during debugging.
     * This can be particularly useful when trying to isolate or identify specific issues within tests.
     */
    public static final LogLevel TEST_LOG_LEVEL = LogLevel.WARN;

    /**
     * Specifies the number of times tests should be repeated to ensure concurrency reliability. This setting aims to strike a balance between thorough testing and practical execution times.
     * It's advised to maintain this value around 100 repeats. Higher values might affect the reliability of timing-sensitive assertions due to the varying capabilities of different testing environments.
     * <p>
     * This concurrency configuration supports the following objectives:
     * - Thread Safety: Ensures that components behave correctly when accessed by multiple threads simultaneously.
     * - Validates Performance Under Load: Confirms that the system can handle high levels of concurrency without significant performance degradation.
     * - Guarantees Correct Event Handling: Verifies that events are processed accurately and in order even when handled concurrently.
     * - Ensures Robustness and Stability: Checks for the resilience of the system under concurrent usage, ensuring it remains stable and performs consistently.
     * - Prepares for Real-World Scenarios: Mimics real-world application usage to ensure the system can handle concurrent operations effectively.
     * - Promotes Confidence in Security: Helps identify potential security vulnerabilities that could be exploited through concurrent execution.
     */
    public static final int TEST_REPEAT = 128;
    public static final int TEST_TIMEOUT = 2048 + (int) (Math.sqrt(TEST_REPEAT) * 50);

    public static boolean await(final CountDownLatch latch) throws InterruptedException {
        return latch.await(TEST_TIMEOUT, MILLISECONDS);
    }
}
