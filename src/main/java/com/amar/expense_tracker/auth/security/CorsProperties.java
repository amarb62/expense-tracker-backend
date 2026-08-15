package com.amar.expense_tracker.auth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
@Getter @Setter
public class CorsProperties {

    private List<String> allowedOrigins;
}
