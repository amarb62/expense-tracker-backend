package com.amar.expense_tracker.config;

import com.amar.expense_tracker.common.filter.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Must run before Spring Security's filter chain so even authentication
        // failures are logged with a correlation ID, not just successful requests.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
