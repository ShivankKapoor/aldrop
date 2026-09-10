package com.shivankkapoor.aldrop.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivankkapoor.aldrop.Data.AuthEvent;
import com.shivankkapoor.aldrop.Data.AuthEventType;
import com.shivankkapoor.aldrop.Data.Location;
import com.shivankkapoor.aldrop.Repository.AuthEventRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger(AuthEventService.class);

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${meridian.base-url}")
    private String meridianBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID platformId, UUID userId, String attemptedUsername, AuthEventType eventType,
            String ipAddress, String userAgent) {
        Location location = getLocation(ipAddress);

        AuthEvent event = new AuthEvent();
        event.setId(UUID.randomUUID());
        event.setPlatformId(platformId);
        event.setUserId(userId);
        event.setAttemptedUsername(attemptedUsername);
        event.setEventType(eventType);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setCity(location.city());
        event.setCountry(location.country());

        try {
            AuthEvent saved = authEventRepository.save(event);
            log.info("Auth event recorded, id={}, eventType={}, platformId={}, userId={}",
                    saved.getId(), eventType, platformId, userId);
        } catch (Exception e) {
            log.error("Unable to save auth event, eventType={}, platformId={}, userId={}",
                    eventType, platformId, userId, e);
        }
    }

    private Location getLocation(String ip) {
        if (ip == null || ip.isBlank()) {
            return Location.NONE;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(meridianBaseUrl + "/location/" + ip))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Meridian returned status {} for IP {}", response.statusCode(), ip);
                return Location.NONE;
            }
            JsonNode json = objectMapper.readTree(response.body());
            return new Location(json.path("city").asText(null), json.path("country").asText(null));
        } catch (Exception e) {
            log.error("Error getting IP location for IP {} from meridian", ip, e);
            return Location.NONE;
        }
    }
}
