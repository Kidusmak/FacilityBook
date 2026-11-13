package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.notification.NotificationResponse;
import com.groupthree.Facilitybook.entity.Notification;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.NotificationRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final int TITLE_MAX = 150;
    private static final int MESSAGE_MAX = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               CurrentUser currentUser) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public void notify(User recipient, NotificationType type, String title, String message,
                       Reservation reservation) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(truncate(title, TITLE_MAX));
        notification.setMessage(truncate(message, MESSAGE_MAX));
        notification.setReservation(reservation);
        notificationRepository.save(notification);
    }

    @Transactional
    public int notifyRole(String roleName, NotificationType type, String title, String message,
                          Reservation reservation) {
        List<User> recipients = userRepository.findByRoleNameAndActiveTrue(roleName);
        for (User recipient : recipients) {
            notify(recipient, type, title, message, reservation);
        }
        return recipients.size();
    }

    @Transactional
    public int notifyRoleInDepartment(String roleName, Long departmentId, NotificationType type,
                                      String title, String message, Reservation reservation) {
        List<User> recipients = departmentId == null
                ? userRepository.findByRoleNameAndActiveTrue(roleName)
                : userRepository.findByRoleNameAndDepartmentIdAndActiveTrue(roleName, departmentId);
        for (User recipient : recipients) {
            notify(recipient, type, title, message, reservation);
        }
        return recipients.size();
    }

    public PageResponse<NotificationResponse> listMine(Pageable pageable) {
        Long userId = currentUser.id();
        return PageResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadFalse(currentUser.id());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Notification " + id + " was not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllReadByUserId(currentUser.id());
    }

    public boolean reminderAlreadySent(Long reservationId) {
        return notificationRepository.existsByReservationIdAndType(
                reservationId, NotificationType.RESERVATION_REMINDER);
    }

    @Transactional
    public void sendReminder(Reservation reservation) {
        notify(reservation.getRequester(),
                NotificationType.RESERVATION_REMINDER,
                "Upcoming reservation",
                "Your reservation for " + reservation.getResource().getName()
                        + " starts at " + reservation.getStartTime() + ".",
                reservation);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
