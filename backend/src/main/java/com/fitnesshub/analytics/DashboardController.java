package com.fitnesshub.analytics;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /** Returns a ClientDashboardDto or CoachDashboardDto depending on the caller's role. */
    @GetMapping("/api/dashboard")
    public ApiResponse<?> dashboard() {
        if (currentUser.isCoach()) {
            return ApiResponse.of(dashboardService.coachDashboard());
        }
        return ApiResponse.of(dashboardService.clientDashboard());
    }

    @GetMapping("/api/coach/dashboard")
    public ApiResponse<?> coachDashboard() {
        return ApiResponse.of(dashboardService.coachDashboard());
    }
}
