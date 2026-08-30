package com.shivankkapoor.aldrop.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class PlatformApiKeyAuthFilterTest {

    private static final String VALID_API_KEY = "valid-api-key";

    private PlatformRepository platformRepository;
    private PlatformApiKeyAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws IOException {
        platformRepository = mock(PlatformRepository.class);
        filter = new PlatformApiKeyAuthFilter(platformRepository);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/auth/login");
    }

    @Test
    void allowsRequestWithValidActiveApiKey() throws ServletException, IOException {
        UUID platformId = UUID.randomUUID();
        Platform platform = new Platform();
        platform.setId(platformId);
        platform.setActive(true);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_API_KEY);
        when(platformRepository.findByApiKey(VALID_API_KEY)).thenReturn(Optional.of(platform));

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE, platformId);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsWhenApiKeyUnknown() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer unknown-key");
        when(platformRepository.findByApiKey("unknown-key")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseBody.toString()).isEqualTo("{\"error\":\"Unauthorized\"}");
    }

    @Test
    void rejectsWhenPlatformIsInactive() throws ServletException, IOException {
        Platform inactivePlatform = new Platform();
        inactivePlatform.setId(UUID.randomUUID());
        inactivePlatform.setActive(false);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_API_KEY);
        when(platformRepository.findByApiKey(VALID_API_KEY)).thenReturn(Optional.of(inactivePlatform));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsWhenNoAuthorizationHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(platformRepository, never()).findByApiKey(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWhenHeaderIsNotBearerScheme() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(platformRepository, never()).findByApiKey(org.mockito.ArgumentMatchers.any());
    }
}
