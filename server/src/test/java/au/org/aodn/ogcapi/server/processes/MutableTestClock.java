package au.org.aodn.ogcapi.server.processes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock the test can wind forward. The download services take a {@link Clock} so their
 * time-based behaviour - hold expiry, the grace on a just-submitted job, snapshot staleness -
 * can be exercised without sleeping.
 */
final class MutableTestClock extends Clock {

    private Instant instant;

    MutableTestClock(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    void advance(Duration amount) {
        instant = instant.plus(amount);
    }
}
