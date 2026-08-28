package com.shivankkapoor.aldrop.Config;

import com.shivankkapoor.aldrop.Filter.PlatformApiKeyAuthFilter;
import com.shivankkapoor.aldrop.Filter.PlatformBasicAuthFilter;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformSecurityConfig {

    @Value("${aldrop.platform-admin.username}")
    private String platformAdminUsername;

    @Value("${aldrop.platform-admin.password}")
    private String platformAdminPassword;

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
    public FilterRegistrationBean<PlatformApiKeyAuthFilter> platformApiKeyAuthFilter() {
        FilterRegistrationBean<PlatformApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PlatformApiKeyAuthFilter(platformRepository));
        registration.addUrlPatterns("/auth/*");
        return registration;
    }
}
