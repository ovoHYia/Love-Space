package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.DashboardResponse;
import com.lovespace.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboards;
    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }
    @GetMapping public DashboardResponse dashboard(Authentication auth) { return dashboards.dashboard(auth); }
}
