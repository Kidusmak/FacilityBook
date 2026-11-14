package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.report.ResourceUsageResponse;
import com.groupthree.Facilitybook.entity.OperatingHour;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.OperatingHourRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.projection.ResourceUsageProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private MaintenanceRecordRepository maintenanceRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private OperatingHourRepository operatingHourRepository;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reservationRepository, maintenanceRepository,
                resourceRepository, operatingHourRepository, ZONE.getId());
    }

    @Test
    void utilizationIsMeasuredAgainstConfiguredOperatingHours() {
        // One ISO week with Mon-Fri 08:00-18:00 gives 50 bookable hours.
        Instant from = ZonedDateTime.of(2026, 11, 16, 0, 0, 0, 0, ZONE).toInstant();
        Instant to = from.plusSeconds(7 * 24 * 3600);

        when(operatingHourRepository.findByResourceIdOrderByDayOfWeekAsc(7L))
                .thenReturn(weekdayHours());
        when(reservationRepository.findResourceUsage(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(usage(7L, "Projector 01", 2L, 5.0)));

        List<ResourceUsageResponse> rows = service.resourceUsage(from, to, 5);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bookedHours()).isEqualTo(5.0);
        assertThat(rows.get(0).utilizationPercent()).isEqualTo(10.0); // 5 of 50 hours
    }

    @Test
    void resourcesWithoutOperatingHoursUseTheWholePeriod() {
        Instant from = ZonedDateTime.of(2026, 11, 16, 0, 0, 0, 0, ZONE).toInstant();
        Instant to = from.plusSeconds(7 * 24 * 3600);

        when(operatingHourRepository.findByResourceIdOrderByDayOfWeekAsc(8L))
                .thenReturn(List.of());
        when(reservationRepository.findResourceUsage(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(usage(8L, "Conference Hall", 4L, 84.0)));

        List<ResourceUsageResponse> rows = service.resourceUsage(from, to, 5);

        assertThat(rows.get(0).utilizationPercent()).isEqualTo(50.0); // 84 of 168 hours
    }

    @Test
    void rejectsReversedReportPeriod() {
        Instant from = ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, ZONE).toInstant();
        Instant to = from.minusSeconds(3600);

        assertThatThrownBy(() -> service.summary(from, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'to' must be after 'from'");
    }

    private List<OperatingHour> weekdayHours() {
        List<OperatingHour> hours = new ArrayList<>();
        for (short day = 1; day <= 5; day++) {
            OperatingHour hour = new OperatingHour();
            hour.setDayOfWeek(day);
            hour.setOpenTime(LocalTime.of(8, 0));
            hour.setCloseTime(LocalTime.of(18, 0));
            hours.add(hour);
        }
        return hours;
    }

    private ResourceUsageProjection usage(Long resourceId, String name, Long count, Double booked) {
        return new ResourceUsageProjection() {
            @Override
            public Long getResourceId() {
                return resourceId;
            }

            @Override
            public String getResourceName() {
                return name;
            }

            @Override
            public Long getReservationCount() {
                return count;
            }

            @Override
            public Double getBookedHours() {
                return booked;
            }
        };
    }
}
