package com.shivankkapoor.aldrop.Filter;

import java.io.IOException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Hides the OpenAPI docs outside QA.
 *
 * <p>The docs describe every endpoint on this service, including the admin ones that create and
 * delete platforms, so they are only served when {@code ENV=QA}. Anything else, including ENV being
 * unset, gets a 404 rather than a 403: a 403 would confirm the docs are there to be found.
 */
public class DocsAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DocsAccessFilter.class);
    private static final String DOCS_ENABLED_ENV = "QA";

    private final String env;

    public DocsAccessFilter(String env) {
        this.env = env;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDocsEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Docs request refused, env={}, uri={}", env, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private boolean isDocsEnabled() {
        return env != null && DOCS_ENABLED_ENV.equals(env.trim().toUpperCase(Locale.ROOT));
    }
}
