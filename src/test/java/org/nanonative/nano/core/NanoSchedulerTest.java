package org.nanonative.nano.core;


import org.junit.jupiter.api.RepeatedTest;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

class NanoSchedulerTest {

    private static final long SHORT_AWAIT_MS = Math.min(TEST_TIMEOUT, 500);

    // --- Integration-style, no "ages" of waiting ---

    @RepeatedTest(TEST_REPEAT)
    void runDaily_executesSoon() throws Exception {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final CountDownLatch hit = new CountDownLatch(1);
            final LocalTime in50ms = LocalTime.now().plusNanos(50_000_000); // ~50ms
            nano.context(this.getClass()).runDaily(hit::countDown, in50ms);
            assertThat(hit.await(SHORT_AWAIT_MS, MILLISECONDS)).isTrue();
        } finally {
            nano.stop(getClass()).waitForStop();
        }
    }

    @RepeatedTest(TEST_REPEAT)
    void runWeekly_executesSoon_sameDay() throws Exception {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final CountDownLatch hit = new CountDownLatch(1);
            final DayOfWeek today = LocalDate.now().getDayOfWeek();
            final LocalTime in50ms = LocalTime.now().plusNanos(50_000_000);
            nano.context(this.getClass()).runWeekly(hit::countDown, in50ms, today);
            assertThat(hit.await(SHORT_AWAIT_MS, MILLISECONDS)).isTrue();
        } finally {
            nano.stop(getClass()).waitForStop();
        }
    }

    @RepeatedTest(TEST_REPEAT)
    void runWeekly_executesSoon_sameDay_withContext() throws Exception {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final CountDownLatch hit = new CountDownLatch(1);
            final DayOfWeek today = LocalDate.now().getDayOfWeek();
            final LocalTime in50ms = LocalTime.now().plusNanos(50_000_000);
            nano.context(this.getClass()).runWeekly(hit::countDown, in50ms, today);
            assertThat(hit.await(SHORT_AWAIT_MS, MILLISECONDS)).isTrue();
        } finally {
            nano.stop(getClass()).waitForStop();
        }
    }

    @RepeatedTest(TEST_REPEAT)
    void run_withUntilStopsAfterFirst() throws Exception {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final CountDownLatch hit = new CountDownLatch(1);
            final AtomicBoolean stop = new AtomicBoolean(false);
            final LocalTime in30ms = LocalTime.now().plusNanos(30_000_000);

            nano.run(nano::context, () -> {
                hit.countDown();
                stop.set(true);
            }, in30ms, null, ZoneId.systemDefault(), stop::get);

            assertThat(hit.await(SHORT_AWAIT_MS, MILLISECONDS)).isTrue();
            // Give it a bit of time to (not) reschedule
            TimeUnit.MILLISECONDS.sleep(50);
            assertThat(hit.getCount()).isZero();
        } finally {
            nano.stop(getClass()).waitForStop();
        }
    }

    @RepeatedTest(TEST_REPEAT)
    void run_dowInPastToday_doesNotRunImmediately() throws Exception {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final CountDownLatch hit = new CountDownLatch(1);
            // choose a time one hour in the past; because dow == today, next run is next week
            final LocalTime past = LocalTime.now().minusHours(1);
            final DayOfWeek today = LocalDate.now().getDayOfWeek();

            nano.run(nano::context, hit::countDown, past, today, ZoneId.systemDefault(), () -> false);

            assertThat(hit.await(100, MILLISECONDS)).isFalse(); // should NOT fire now
        } finally {
            nano.stop(getClass()).waitForStop();
        }
    }

    // --- Pure function tests: instant, fast, DST-aware ---

    @RepeatedTest(TEST_REPEAT)
    void resolveWallTime_springForward_gap_pushedForward_EuropeBerlin() {
        final ZoneId zone = ZoneId.of("Europe/Berlin");
        // In 2025, DST starts on 2025-03-30 at 02:00 -> clocks jump to 03:00. 02:30 is invalid.
        final LocalDate date = LocalDate.of(2025, 3, 30);
        final LocalTime invalid0230 = LocalTime.of(2, 30);

        final ZonedDateTime zdt = NanoThreads.resolveWallTime(date, invalid0230, zone);

        assertThat(zdt.getYear()).isEqualTo(2025);
        assertThat(zdt.getMonthValue()).isEqualTo(3);
        // pushed to the first valid instant after the gap (~03:30 local)
        assertThat(zdt.getHour()).isEqualTo(3);
        assertThat(zdt.getMinute()).isEqualTo(30);
        assertThat(zdt.getZone()).isEqualTo(zone);
    }

    @RepeatedTest(TEST_REPEAT)
    void resolveWallTime_fallBack_overlap_picksEarlierOffset_EuropeBerlin() {
        final ZoneId zone = ZoneId.of("Europe/Berlin");
        // In 2025, DST ends on 2025-10-26: 03:00 CEST -> 02:00 CET (02:xx occurs twice)
        final LocalDate date = LocalDate.of(2025, 10, 26);
        final LocalTime overlapped0230 = LocalTime.of(2, 30);
        final LocalDateTime ldt = LocalDateTime.of(date, overlapped0230);

        final ZonedDateTime zdt = NanoThreads.resolveWallTime(date, overlapped0230, zone);
        // ZoneRules lists valid offsets in order; first is the earlier offset (summer time)
        final ZoneOffset earlier = zone.getRules().getValidOffsets(ldt).getFirst();

        assertThat(zone.getRules().isValidOffset(ldt, earlier)).isTrue();
        assertThat(zdt.getOffset()).isEqualTo(earlier);
        assertThat(zdt.toLocalTime()).isEqualTo(overlapped0230);
    }

    @RepeatedTest(TEST_REPEAT)
    void initialPlanned_daily_rollsForwardWhenPastToday() {
        final ZoneId zone = ZoneId.systemDefault();
        final LocalTime past = LocalTime.now(zone).minusMinutes(1);

        final ZonedDateTime first = NanoThreads.initialPlanned(past, null, zone);

        assertThat(first.toLocalTime()).isEqualTo(past);
        assertThat(first.toLocalDate()).isEqualTo(LocalDate.now(zone).plusDays(1));
    }

    @RepeatedTest(TEST_REPEAT)
    void initialPlanned_weekly_nextOrSame_thenNextWeekWhenPast() {
        final ZoneId zone = ZoneId.systemDefault();
        final DayOfWeek today = LocalDate.now(zone).getDayOfWeek();

        final LocalTime past = LocalTime.now(zone).minusMinutes(1);
        final ZonedDateTime weekly = NanoThreads.initialPlanned(past, today, zone);

        assertThat(weekly.getDayOfWeek()).isEqualTo(today);
        assertThat(weekly.toLocalDate()).isEqualTo(LocalDate.now(zone).plusWeeks(1));
        assertThat(weekly.toLocalTime()).isEqualTo(past);
    }

    @RepeatedTest(TEST_REPEAT)
    void nextPlanned_preservesWallClock_acrossDST() {
        final ZoneId zone = ZoneId.of("Europe/Berlin");
        final LocalTime t = LocalTime.of(7, 0);

        // Choose a date right before spring-forward weekend
        final ZonedDateTime prev = NanoThreads.resolveWallTime(LocalDate.of(2025, 3, 29), t, zone);
        final ZonedDateTime next = NanoThreads.nextPlanned(prev, t, null, zone);

        assertThat(next.toLocalTime()).isEqualTo(t); // still 07:00 local the next day
        // The duration between instants is likely 23h; we only care that wall time stays 07:00
        assertThat(Duration.between(prev.toInstant(), next.toInstant()).toHours()).isIn(23L, 24L, 25L);
    }
}
