package com.shivankkapoor.aldrop.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shivankkapoor.aldrop.Filter.DocsAccessFilter;
import com.shivankkapoor.aldrop.Filter.PlatformApiKeyAuthFilter;
import com.shivankkapoor.aldrop.Filter.PlatformBasicAuthFilter;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;

@Configuration
public class PlatformSecurityConfig {

    private static final String[] DOCS_URL_PATTERNS = {
            "/v3/api-docs", "/v3/api-docs/*", "/swagger-ui", "/swagger-ui/*", "/swagger-ui.html"
    };

    @Value("${aldrop.platform-admin.username}")
    private String platformAdminUsername;

    @Value("${aldrop.platform-admin.password}")
    private String platformAdminPassword;

    @Value("${aldrop.env:}")
    private String env;

    @Autowired
    private PlatformRepository platformRepository;

    @Bean
    public FilterRegistrationBean<PlatformBasicAuthFilter> platformBasicAuthFilter() {
        FilterRegistrationBean<PlatformBasicAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PlatformBasicAuthFilter(platformAdminUsername, platformAdminPassword));
        registration.addUrlPatterns("/platform/*");
        return registration;
    }

   
    @Bean
    public FilterRegistrationBean<DocsAccessFilter> docsAccessFilter() {
        FilterRegistrationBean<DocsAccessFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DocsAccessFilter(env));
        registration.addUrlPatterns(DOCS_URL_PATTERNS);
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PlatformBasicAuthFilter> docsBasicAuthFilter() {
        FilterRegistrationBean<PlatformBasicAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PlatformBasicAuthFilter(platformAdminUsername, platformAdminPassword));
        registration.addUrlPatterns(DOCS_URL_PATTERNS);
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PlatformApiKeyAuthFilter> platformApiKeyAuthFilter() {
        FilterRegistrationBean<PlatformApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PlatformApiKeyAuthFilter(platformRepository));
        registration.addUrlPatterns("/auth/*");
        return registration;
    }
}
