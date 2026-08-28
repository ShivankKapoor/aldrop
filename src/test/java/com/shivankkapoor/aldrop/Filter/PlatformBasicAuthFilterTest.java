package com.shivankkapoor.aldrop.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class PlatformBasicAuthFilterTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "s3cr3t";

    private PlatformBasicAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws IOException {
        filter = new PlatformBasicAuthFilter(USERNAME, PASSWORD);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/platform/create");
    }

    @Test
    void allowsRequestWithCorrectCredentials() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(basicHeader(USERNAME, PASSWORD));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @ParameterizedTest
    @MethodSource("rejectedHeaders")
    void rejectsRequestWithInvalidCredentials(String header) throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(header);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setHeader("WWW-Authenticate", "Basic realm=\"aldrop-platform\"");
        assertThat(responseBody.toString()).isEqualTo("{\"error\":\"Unauthorized\"}");
    }

    private static Stream<String> rejectedHeaders() {
        return Stream.of(
                null,
                "",
                "Bearer " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes()),
                basicHeader(USERNAME, "wrong-password"),
                basicHeader("wrong-user", PASSWORD),
                "Basic " + Base64.getEncoder().encodeToString("no-colon-here".getBytes()),
                "Basic not-valid-base64!!!"
        );
    }

    private static String basicHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
