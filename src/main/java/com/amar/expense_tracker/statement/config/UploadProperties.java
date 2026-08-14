package com.amar.expense_tracker.statement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
@Getter
@Setter
public class UploadProperties {

    private long maxFileSizeBytes;
    private String allowedExtensions;
}
