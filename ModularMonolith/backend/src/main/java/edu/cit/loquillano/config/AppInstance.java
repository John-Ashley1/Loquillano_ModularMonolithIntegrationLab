package edu.cit.loquillano.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity of THIS running copy of the application (Lab 4, Task 1).
 *
 * A new random UUID is generated every time the JVM starts and is sent as
 * X-Client-Instance on every call to Tiangge and LegacySupply. It lives in
 * the shared config package (not in channel) so that the supplier module
 * can stamp its calls without depending on the channel module.
 */
@Component
public class AppInstance {

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("Application instance ID: {}", id);
    }

    public String getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
