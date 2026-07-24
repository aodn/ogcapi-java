package au.org.aodn.ogcapi.server.core.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures log4j2 events emitted by a single class so tests can assert on log
 * level and content (e.g. that download failures are logged at ERROR for New
 * Relic). Unit tests do not load log4j2-spring.xml, so attaching also lowers
 * the logger level to DEBUG to make sure events reach the appender.
 */
public class TestLogAppender extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private TestLogAppender(String name) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
    }

    public static TestLogAppender attachTo(Class<?> clazz) {
        TestLogAppender appender = new TestLogAppender("test-" + clazz.getSimpleName());
        appender.start();
        Configurator.setLevel(clazz.getName(), Level.DEBUG);
        ((Logger) LogManager.getLogger(clazz)).addAppender(appender);
        return appender;
    }

    public void detachFrom(Class<?> clazz) {
        ((Logger) LogManager.getLogger(clazz)).removeAppender(this);
        stop();
    }

    public List<LogEvent> eventsAtLevel(Level level) {
        return events.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }
}
