package edu.cit.loquillano.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled. Without this annotation NOTHING annotated with
 * @Scheduled ever runs (the Lab 3 tracking/resend jobs included). The pool
 * size is set in application.properties (spring.task.scheduling.pool.size)
 * so a slow job can never starve the 30-second heartbeat.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
