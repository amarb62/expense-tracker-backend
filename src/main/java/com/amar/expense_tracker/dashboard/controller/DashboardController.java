package com.amar.expense_tracker.dashboard.controller;

import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import com.amar.expense_tracker.dashboard.dto.MonthlyDashboardResponse;
import com.amar.expense_tracker.dashboard.dto.TrendsResponse;
import com.amar.expense_tracker.dashboard.dto.YearlyDashboardResponse;
import com.amar.expense_tracker.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Monthly/yearly/trend financial summaries")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/monthly")
    @Operation(summary = "Get a month's totals and expense-category breakdown",
            description = "Without accountId, reads the maintained all-accounts summary. With accountId, "
                    + "computes live for just that account (not persisted).")
    public MonthlyDashboardResponse monthly(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam int year,
                                             @RequestParam int month,
                                             @RequestParam(required = false) UUID accountId) {
        return dashboardService.monthly(user.userId(), year, month, accountId);
    }

    @GetMapping("/yearly")
    @Operation(summary = "Get a year's totals, expense-category breakdown, and monthly breakdown")
    public YearlyDashboardResponse yearly(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam int year,
                                           @RequestParam(required = false) UUID accountId) {
        return dashboardService.yearly(user.userId(), year, accountId);
    }

    @GetMapping("/trends")
    @Operation(summary = "List all months of credited/expense totals, oldest to newest")
    public TrendsResponse trends(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.trends(user.userId());
    }
}
