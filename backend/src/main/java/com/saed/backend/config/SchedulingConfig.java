package com.saed.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el soporte de tareas programadas (@Scheduled) en SAED 2.0.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
