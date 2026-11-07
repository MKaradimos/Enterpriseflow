package com.enterpriseflow.dashboard;

import com.enterpriseflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single endpoint that returns everything the dashboard page needs in one round trip. */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "KPIs, charts and work lists, scoped to the caller's projects")
    public DashboardResponse dashboard(CurrentUser user) {
        return dashboardService.build(user);
    }
}
