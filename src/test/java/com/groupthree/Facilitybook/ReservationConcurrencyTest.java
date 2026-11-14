package com.groupthree.Facilitybook;

import com.groupthree.Facilitybook.dto.reservation.ReservationCreateRequest;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceCategoryRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.UserPrincipal;
import com.groupthree.Facilitybook.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the double-booking guard is enforced by PostgreSQL and not only by the
 * service-level "check then insert". Two competing writes are fired at the same time
 * against a real Postgres instance started by Testcontainers.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.reminders.enabled=false")
class ReservationConcurrencyTest {

    private static final Set<ReservationStatus> BLOCKING_STATUSES =
            Set.of(ReservationStatus.PENDING_APPROVAL, ReservationStatus.CONFIRMED);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ResourceCategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void serviceCreatesExactlyOneOfTwoConcurrentOverlappingReservations() throws Exception {
        Resource resource = createResource();
        UserPrincipal principal = employeePrincipal();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        ReservationCreateRequest request =
                new ReservationCreateRequest(resource.getId(), "Concurrent booking", null, start, end);

        Callable<Boolean> booking = () -> {
            authenticate(principal);
            try {
                reservationService.create(request);
                return true;
            } catch (RuntimeException ex) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        List<Boolean> results = runConcurrently(booking);

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(activeReservations(resource.getId(), start, end)).hasSize(1);
    }

    @Test
    void databaseExclusionConstraintRejectsOverlappingInserts() throws Exception {
        Resource resource = createResource();
        User requester = userRepository.findByEmail("employee@facilitybook.local").orElseThrow();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Callable<Boolean> booking = () -> {
            Reservation reservation = new Reservation();
            reservation.setResource(resource);
            reservation.setRequester(requester);
            reservation.setPurpose("Direct concurrent insert");
            reservation.setStartTime(start);
            reservation.setEndTime(end);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            try {
                reservationRepository.saveAndFlush(reservation);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        };

        List<Boolean> results = runConcurrently(booking);

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(activeReservations(resource.getId(), start, end)).hasSize(1);
    }

    private Resource createResource() {
        ResourceCategory category = categoryRepository.findByNameIgnoreCase("Meeting Room")
                .orElseThrow();
        Resource resource = new Resource();
        resource.setName("Concurrency Room " + UUID.randomUUID());
        resource.setCategory(category);
        resource.setStatus(ResourceStatus.ACTIVE);
        return resourceRepository.save(resource);
    }

    private UserPrincipal employeePrincipal() {
        User employee = userRepository.findByEmail("employee@facilitybook.local").orElseThrow();
        return UserPrincipal.from(employee);
    }

    private void authenticate(UserPrincipal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private List<Reservation> activeReservations(Long resourceId, Instant start, Instant end) {
        return reservationRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        resourceId, BLOCKING_STATUSES, end.plus(1, ChronoUnit.HOURS),
                        start.minus(1, ChronoUnit.HOURS));
    }

    private List<Boolean> runConcurrently(Callable<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
