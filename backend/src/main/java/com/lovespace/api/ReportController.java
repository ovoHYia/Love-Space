package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.MonthlyReportResponse;
import com.lovespace.service.MonthlyReportService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final MonthlyReportService reports;

    public ReportController(MonthlyReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/monthly")
    public MonthlyReportResponse monthly(
            Authentication auth,
            @RequestParam(required = false) String month) {
        return reports.monthly(auth, month);
    }
}
