package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.report.DepartmentUsageResponse;
import com.groupthree.Facilitybook.dto.report.PeakTimeResponse;
import com.groupthree.Facilitybook.dto.report.ReportSummaryResponse;
import com.groupthree.Facilitybook.dto.report.ResourceUsageResponse;
import com.groupthree.Facilitybook.entity.OperatingHour;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.OperatingHourRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.projection.DepartmentUsageProjection;
import com.groupthree.Facilitybook.repository.projection.HourUsageProjection;
import com.groupthree.Facilitybook.repository.projection.ResourceUsageProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReservationRepository reservationRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final ResourceRepository resourceRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final ZoneId zone;

    public ReportService(ReservationRepository reservationRepository,
                         MaintenanceRecordRepository maintenanceRepository,
                         ResourceRepository resourceRepository,
                         OperatingHourRepository operatingHourRepository,
                         @Value("${app.timezone:Europe/Istanbul}") String zone) {
        this.reservationRepository = reservationRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.resourceRepository = resourceRepository;
        this.operatingHourRepository = operatingHourRepository;
        this.zone = ZoneId.of(zone);
    }

    public ReportSummaryResponse summary(Instant from, Instant to) {
        Period period = normalize(from, to);

        long total = reservationRepository
                .countByStartTimeGreaterThanEqualAndStartTimeLessThan(period.from(), period.to());
        long confirmed = reservationRepository
                .countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        ReservationStatus.CONFIRMED, period.from(), period.to());
        long pending = reservationRepository
                .countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        ReservationStatus.PENDING_APPROVAL, period.from(), period.to());
        long cancelled = reservationRepository
                .countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        ReservationStatus.CANCELLED, period.from(), period.to());
        long rejected = reservationRepository
                .countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        ReservationStatus.REJECTED, period.from(), period.to());
        long flagged = reservationRepository
                .countByNeedsResolutionTrueAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        period.from(), period.to());
        long activeResources = resourceRepository.countByStatus(ResourceStatus.ACTIVE);
        long maintenanceRecords = maintenanceRepository
                .countByStartTimeLessThanAndEndTimeGreaterThan(period.to(), period.from());

        double bookedHours = nullSafe(reservationRepository
                .sumConfirmedBookedHours(period.from(), period.to()));
        double downtimeHours = nullSafe(maintenanceRepository
                .sumDowntimeHours(period.from(), period.to()));

        return new ReportSummaryResponse(period.from(), period.to(), total, confirmed, pending,
                cancelled, rejected, flagged, activeResources, maintenanceRecords,
                round(bookedHours), round(downtimeHours));
    }

    public List<ResourceUsageResponse> resourceUsage(Instant from, Instant to, int limit) {
        Period period = normalize(from, to);
        int size = Math.max(1, Math.min(limit, 50));

        List<ResourceUsageProjection> rows = reservationRepository.findResourceUsage(
                period.from(), period.to(), PageRequest.of(0, size));

        return rows.stream()
                .map(row -> {
                    double booked = nullSafe(row.getBookedHours());
                    double available = availableHours(row.getResourceId(), period);
                    double utilization = available <= 0 ? 0 : (booked / available) * 100.0;
                    return new ResourceUsageResponse(
                            row.getResourceId(),
                            row.getResourceName(),
                            row.getReservationCount() != null ? row.getReservationCount() : 0,
                            round(booked),
                            round(Math.min(utilization, 100.0)));
                })
                .toList();
    }

    public List<DepartmentUsageResponse> departmentUsage(Instant from, Instant to) {
        Period period = normalize(from, to);
        return reservationRepository.findDepartmentUsage(period.from(), period.to()).stream()
                .map(row -> new DepartmentUsageResponse(
                        row.getDepartmentId(),
                        row.getDepartmentName(),
                        row.getReservationCount() != null ? row.getReservationCount() : 0))
                .toList();
    }

    public List<PeakTimeResponse> peakTimes(Instant from, Instant to) {
        Period period = normalize(from, to);
        return reservationRepository.findPeakHours(period.from(), period.to(), zone.getId()).stream()
                .map(row -> new PeakTimeResponse(
                        row.getHour() != null ? row.getHour() : 0,
                        row.getReservationCount() != null ? row.getReservationCount() : 0))
                .toList();
    }

    /**
     * Hours the resource could actually be booked inside the report period, derived from its
     * operating-hour rows. Resources without configured hours are treated as always available.
     * Utilization is therefore booked hours divided by these available hours, capped at 100%.
     */
    private double availableHours(Long resourceId, Period period) {
        List<OperatingHour> hours = operatingHourRepository
                .findByResourceIdOrderByDayOfWeekAsc(resourceId);
        if (hours.isEmpty()) {
            return Duration.between(period.from(), period.to()).toMinutes() / 60.0;
        }

        Map<Short, List<OperatingHour>> byDay = new HashMap<>();
        for (OperatingHour hour : hours) {
            byDay.computeIfAbsent(hour.getDayOfWeek(), key -> new ArrayList<>()).add(hour);
        }

        double total = 0;
        LocalDate date = period.from().atZone(zone).toLocalDate();
        LocalDate lastDate = period.to().minusNanos(1).atZone(zone).toLocalDate();
        while (!date.isAfter(lastDate)) {
            for (OperatingHour hour : byDay.getOrDefault(
                    (short) date.getDayOfWeek().getValue(), List.of())) {
                Instant open = LocalDateTime.of(date, hour.getOpenTime()).atZone(zone).toInstant();
                Instant close = LocalDateTime.of(date, hour.getCloseTime()).atZone(zone).toInstant();
                Instant windowStart = open.isBefore(period.from()) ? period.from() : open;
                Instant windowEnd = close.isAfter(period.to()) ? period.to() : close;
                if (windowEnd.isAfter(windowStart)) {
                    total += Duration.between(windowStart, windowEnd).toMinutes() / 60.0;
                }
            }
            date = date.plusDays(1);
        }
        return total;
    }

    private Period normalize(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant end = to != null ? to : now.plus(30, ChronoUnit.DAYS);
        Instant start = from != null ? from : now.minus(30, ChronoUnit.DAYS);
        if (!end.isAfter(start)) {
            throw new BadRequestException("'to' must be after 'from'");
        }
        return new Period(start, end);
    }

    private double nullSafe(Double value) {
        return value != null ? value : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Period(Instant from, Instant to) {
    }
}
