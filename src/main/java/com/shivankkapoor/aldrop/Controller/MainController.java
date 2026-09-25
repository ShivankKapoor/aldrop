package com.shivankkapoor.aldrop.Controller;

import com.shivankkapoor.aldrop.Cache.DataCacheRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private DataCacheRegistry dataCacheRegistry;

    @Operation(summary = "Landing page", description = "A static page confirming the service is reachable.")
    @GetMapping("/")
    public ResponseEntity<String> home(){
        log.info("Home endpoint has been called");
        final String homeString = "<html><body><h2>Welcome to Aldrop!</h2></body></html>";
        return ResponseEntity.ok(homeString);
    }

    @Operation(summary = "Liveness and uptime",
            description = "Reports that the service is up, how long it has been running, and the hit rate "
                    + "of each cached lookup since start.")
    @GetMapping("/monitor")
    public ResponseEntity<Map<String, Object>> monitor(){
        log.info("Monitor endpoint has been called");
        Map<String, Object> resp = new LinkedHashMap<>();
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
        resp.put("cache", dataCacheRegistry.stats());

        return ResponseEntity.ok(resp);

    }
}
