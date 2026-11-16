package com.bidv.coretraining.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness probe for the container healthcheck. It does no work, so the probe neither loads a
 * user from the database nor fills the request log (RequestLoggingFilter logs it at DEBUG).
 */
@RestController
public class HealthController {
    public static final String PATH = "/api/health";

    @GetMapping(PATH)
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
