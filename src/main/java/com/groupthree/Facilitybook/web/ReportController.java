package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.report.DepartmentUsageResponse;
import com.groupthree.Facilitybook.dto.report.PeakTimeResponse;
import com.groupthree.Facilitybook.dto.report.ReportSummaryResponse;
import com.groupthree.Facilitybook.dto.report.ResourceUsageResponse;
import com.groupthree.Facilitybook.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN','FACILITY_MANAGER','DEPARTMENT_MANAGER','RESOURCE_OPERATOR')")
@Tag(name = "Reports",
        description = "Reservation, utilization, department, peak-time and downtime analytics.")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.summary(from, to);
    }

    @GetMapping("/resources")
    public List<ResourceUsageResponse> resources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.resourceUsage(from, to, limit);
    }

    @GetMapping("/departments")
    public List<DepartmentUsageResponse> departments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.departmentUsage(from, to);
    }

    @GetMapping("/peak-times")
    public List<PeakTimeResponse> peakTimes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.peakTimes(from, to);
    }
}
