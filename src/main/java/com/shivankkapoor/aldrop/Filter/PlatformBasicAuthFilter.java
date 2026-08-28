package com.shivankkapoor.aldrop.Filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PlatformBasicAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformBasicAuthFilter.class);
    private static final String BASIC_PREFIX = "Basic ";

    private final byte[] expectedUsername;
    private final byte[] expectedPassword;

    public PlatformBasicAuthFilter(String username, String password) {
        this.expectedUsername = username.getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String[] credentials = header != null && header.startsWith(BASIC_PREFIX)
                ? decode(header.substring(BASIC_PREFIX.length()))
                : null;

        if (credentials != null && isValid(credentials[0], credentials[1])) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Platform endpoint authentication failed for {} {}", request.getMethod(), request.getRequestURI());
        response.setHeader("WWW-Authenticate", "Basic realm=\"aldrop-platform\"");
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    private String[] decode(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return null;
            }
            return new String[]{decoded.substring(0, separator), decoded.substring(separator + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isValid(String username, String password) {
        return MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8), expectedUsername)
                && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), expectedPassword);
    }
}
