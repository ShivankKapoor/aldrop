package com.shivankkapoor.aldrop.Filter;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PlatformApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String PLATFORM_ID_ATTRIBUTE = "platformId";

    private static final Logger log = LoggerFactory.getLogger(PlatformApiKeyAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformRepository platformRepository;

    public PlatformApiKeyAuthFilter(PlatformRepository platformRepository) {
        this.platformRepository = platformRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String apiKey = header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length())
                : null;

        Optional<Platform> platform = apiKey != null
                ? platformRepository.findByApiKey(apiKey).filter(Platform::isActive)
                : Optional.empty();

        if (platform.isPresent()) {
            request.setAttribute(PLATFORM_ID_ATTRIBUTE, platform.get().getId());
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Platform API key authentication failed for {} {}", request.getMethod(), request.getRequestURI());
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
