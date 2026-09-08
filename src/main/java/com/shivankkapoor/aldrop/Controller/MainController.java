package com.shivankkapoor.aldrop.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "Service", description = "Unauthenticated liveness endpoints.")
public class MainController {

    private final Instant startTime = Instant.now();
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @Operation(summary = "Landing page", description = "A static page confirming the service is reachable.")
    @GetMapping("/")
    public ResponseEntity<String> home(){
        log.info("Home endpoint has been called");
        final String homeString = "<html><body><h2>Welcome to Aldrop!</h2></body></html>";
        return ResponseEntity.ok(homeString);
    }

    @Operation(summary = "Liveness and uptime",
            description = "Reports that the service is up and how long it has been running.")
    @GetMapping("/monitor")
    public ResponseEntity<Map<String, String>> monitor(){
        log.info("Monitor endpoint has been called");
        Map<String, String> resp = new LinkedHashMap<>();
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        String uptimeStr = (days > 0 ? days + "d " : "") + hours + "h " + minutes + "m " + seconds + "s";

        resp.put("name", "Aldrop");
        resp.put("status", "Up");
        resp.put("uptime", uptimeStr);
        resp.put("platform", "Java");
        resp.put("version", System.getProperty("java.version"));
        resp.put("runtime",System.getProperty("java.runtime.name"));
        resp.put("vendor", System.getProperty("java.vendor"));

        return ResponseEntity.ok(resp);

    }
}
