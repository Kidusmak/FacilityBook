package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.resource.OperatingHourRequest;
import com.groupthree.Facilitybook.dto.resource.OperatingHourResponse;
import com.groupthree.Facilitybook.dto.resource.ResourceRequest;
import com.groupthree.Facilitybook.dto.resource.ResourceResponse;
import com.groupthree.Facilitybook.entity.Building;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.OperatingHour;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.BuildingRepository;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.OperatingHourRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceCategoryRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ResourceService {

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.PENDING_APPROVAL, ReservationStatus.CONFIRMED);
    private static final Set<MaintenanceStatus> BLOCKING_MAINTENANCE_STATUSES =
            Set.of(MaintenanceStatus.REPORTED, MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS);

    private final ResourceRepository resourceRepository;
    private final ResourceCategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final ReservationRepository reservationRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;

    public ResourceService(ResourceRepository resourceRepository,
                           ResourceCategoryRepository categoryRepository,
                           BuildingRepository buildingRepository,
                           OperatingHourRepository operatingHourRepository,
                           ReservationRepository reservationRepository,
                           MaintenanceRecordRepository maintenanceRecordRepository) {
        this.resourceRepository = resourceRepository;
        this.categoryRepository = categoryRepository;
        this.buildingRepository = buildingRepository;
        this.operatingHourRepository = operatingHourRepository;
        this.reservationRepository = reservationRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
    }

    public PageResponse<ResourceResponse> list(String search, Long categoryId, Long buildingId,
                                               ResourceStatus status, Integer minCapacity,
                                               Pageable pageable) {
        Specification<Resource> specification = baseSpecification(
                search, categoryId, buildingId, status, minCapacity);
        Page<Resource> page = resourceRepository.findAll(specification, pageable);
        return PageResponse.from(page, ResourceResponse::from);
    }

    /**
     * Returns resources that are ACTIVE and free of any reservation or active maintenance
     * window overlapping the requested period.
     */
    public PageResponse<ResourceResponse> searchAvailable(Instant startTime, Instant endTime,
                                                          String search, Long categoryId,
                                                          Long buildingId, Integer minCapacity,
                                                          Pageable pageable) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Both startTime and endTime are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("endTime must be after startTime");
        }

        Specification<Resource> specification = baseSpecification(
                search, categoryId, buildingId, ResourceStatus.ACTIVE, minCapacity)
                .and((root, query, cb) -> {
                    Subquery<Long> reservationSubquery = query.subquery(Long.class);
                    Root<Reservation> reservation = reservationSubquery.from(Reservation.class);
                    reservationSubquery.select(cb.literal(1L)).where(
                            cb.equal(reservation.get("resource").get("id"), root.get("id")),
                            reservation.get("status").in(BLOCKING_RESERVATION_STATUSES),
                            cb.lessThan(reservation.get("startTime"), endTime),
                            cb.greaterThan(reservation.get("endTime"), startTime));

                    Subquery<Long> maintenanceSubquery = query.subquery(Long.class);
                    Root<MaintenanceRecord> maintenance = maintenanceSubquery.from(MaintenanceRecord.class);
                    maintenanceSubquery.select(cb.literal(1L)).where(
                            cb.equal(maintenance.get("resource").get("id"), root.get("id")),
                            maintenance.get("status").in(BLOCKING_MAINTENANCE_STATUSES),
                            cb.lessThan(maintenance.get("startTime"), endTime),
                            cb.greaterThan(maintenance.get("endTime"), startTime));

                    return cb.and(cb.not(cb.exists(reservationSubquery)),
                            cb.not(cb.exists(maintenanceSubquery)));
                });

        Page<Resource> page = resourceRepository.findAll(specification, pageable);
        return PageResponse.from(page, ResourceResponse::from);
    }

    public ResourceResponse get(Long id) {
        Resource resource = find(id);
        return ResourceResponse.from(resource, operatingHours(id));
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        Resource resource = new Resource();
        apply(resource, request);
        Resource saved = resourceRepository.save(resource);
        return ResourceResponse.from(saved, List.of());
    }

    @Transactional
    public ResourceResponse update(Long id, ResourceRequest request) {
        Resource resource = find(id);
        apply(resource, request);
        Resource saved = resourceRepository.save(resource);
        return ResourceResponse.from(saved, operatingHours(id));
    }

    @Transactional
    public ResourceResponse updateStatus(Long id, ResourceStatus status) {
        Resource resource = find(id);
        resource.setStatus(status);
        Resource saved = resourceRepository.save(resource);
        return ResourceResponse.from(saved, operatingHours(id));
    }

    @Transactional
    public void delete(Long id) {
        Resource resource = find(id);
        if (reservationRepository.existsByResourceId(id)) {
            throw new ConflictException(
                    "Resource has reservation history and cannot be deleted; set its status to INACTIVE instead");
        }
        if (maintenanceRecordRepository.existsByResourceId(id)) {
            throw new ConflictException(
                    "Resource has maintenance history and cannot be deleted; set its status to INACTIVE instead");
        }
        resourceRepository.delete(resource);
    }

    public List<OperatingHourResponse> operatingHours(Long resourceId) {
        find(resourceId);
        return operatingHourRepository.findByResourceIdOrderByDayOfWeekAsc(resourceId).stream()
                .map(OperatingHourResponse::from)
                .toList();
    }

    @Transactional
    public List<OperatingHourResponse> replaceOperatingHours(Long resourceId,
                                                             List<OperatingHourRequest> requests) {
        Resource resource = find(resourceId);

        Set<Short> days = new HashSet<>();
        List<OperatingHour> hours = new ArrayList<>();
        for (OperatingHourRequest request : requests) {
            if (!days.add(request.dayOfWeek())) {
                throw new BadRequestException(
                        "Operating hours contain more than one entry for day " + request.dayOfWeek());
            }
            if (!request.closeTime().isAfter(request.openTime())) {
                throw new BadRequestException("closeTime must be after openTime for day " + request.dayOfWeek());
            }
            OperatingHour hour = new OperatingHour();
            hour.setDayOfWeek(request.dayOfWeek());
            hour.setOpenTime(request.openTime());
            hour.setCloseTime(request.closeTime());
            hours.add(hour);
        }

        operatingHourRepository.deleteAllByResourceId(resourceId);
        Resource refreshed = find(resourceId);
        hours.forEach(hour -> hour.setResource(refreshed));
        hours.sort(Comparator.comparing(OperatingHour::getDayOfWeek));
        return operatingHourRepository.saveAll(hours).stream()
                .map(OperatingHourResponse::from)
                .toList();
    }

    private Specification<Resource> baseSpecification(String search, Long categoryId, Long buildingId,
                                                      ResourceStatus status, Integer minCapacity) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("features")), pattern),
                        cb.like(cb.lower(root.get("locationDetail")), pattern)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (buildingId != null) {
                predicates.add(cb.equal(root.get("building").get("id"), buildingId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (minCapacity != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void apply(Resource resource, ResourceRequest request) {
        ResourceCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Resource category " + request.categoryId() + " was not found"));

        Building building = null;
        if (request.buildingId() != null) {
            building = buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new NotFoundException(
                            "Building " + request.buildingId() + " was not found"));
        }

        resource.setName(request.name().trim());
        resource.setDescription(trimToNull(request.description()));
        resource.setCategory(category);
        resource.setBuilding(building);
        resource.setFloor(trimToNull(request.floor()));
        resource.setLocationDetail(trimToNull(request.locationDetail()));
        resource.setCapacity(request.capacity());
        resource.setFeatures(trimToNull(request.features()));
        resource.setStatus(request.status() != null ? request.status() : ResourceStatus.ACTIVE);
    }

    private Resource find(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource " + id + " was not found"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
