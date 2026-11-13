package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Creates one in-app reminder per upcoming confirmed reservation. A notification row is
 * used as the "already reminded" marker, so restarting the app cannot duplicate reminders.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final boolean enabled;
    private final long horizonHours;

    public ReminderService(ReservationRepository reservationRepository,
                           NotificationService notificationService,
                           @Value("${app.reminders.enabled:true}") boolean enabled,
                           @Value("${app.reminders.horizon-hours:24}") long horizonHours) {
        this.reservationRepository = reservationRepository;
        this.notificationService = notificationService;
        this.enabled = enabled;
        this.horizonHours = horizonHours;
    }

    @Scheduled(fixedDelayString = "${app.reminders.interval-ms:3600000}", initialDelayString = "60000")
    @Transactional
    public void sendUpcomingReminders() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        Instant horizon = now.plus(horizonHours, ChronoUnit.HOURS);
        List<Reservation> upcoming = reservationRepository
                .findByStatusAndStartTimeBetween(ReservationStatus.CONFIRMED, now, horizon);

        int sent = 0;
        for (Reservation reservation : upcoming) {
            if (!notificationService.reminderAlreadySent(reservation.getId())) {
                notificationService.sendReminder(reservation);
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Sent {} upcoming reservation reminder(s)", sent);
        }
    }
}
