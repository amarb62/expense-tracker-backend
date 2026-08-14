package com.amar.expense_tracker.categorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense-ai")
@Getter
@Setter
public class ExpenseAiProperties {

    private boolean enabled;
    private String provider;
    private Confidence confidence = new Confidence();
    private Local local = new Local();

    @Getter
    @Setter
    public static class Confidence {
        private double autoApprove;
        private double review;
    }

    @Getter
    @Setter
    public static class Local {
        private String baseUrl;
        private String model;
    }
}
