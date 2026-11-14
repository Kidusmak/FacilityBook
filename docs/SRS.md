# Software Requirements Specification — FacilityBook

**Project:** FacilityBook — Enterprise Facility & Resource Reservation System
**Version:** 1.0
**Status:** Implemented
**Team:** Group Three

---

## 1. Introduction

### 1.1 Purpose
This document specifies the requirements for FacilityBook, a web application that lets an
organisation share and book common resources (meeting rooms, conference halls, training
rooms, projectors, company vehicles, office equipment) without double-booking, and lets
authorized staff manage resources, approvals, maintenance, and reporting.

### 1.2 Scope
FacilityBook is a three-tier web application:

- a React + TypeScript single-page application,
- a Spring Boot REST API secured with JWT,
- a PostgreSQL database whose schema is managed exclusively through Flyway migrations.

The system covers resource management, reservation management with database-level
conflict prevention, a configurable approval workflow, resource maintenance, in-app
notifications, and analytics/reporting.

### 1.3 Intended audience
Interns, mentors, and reviewers evaluating the internship project, plus any developer who
needs to understand or extend the system.

### 1.4 Definitions and abbreviations
| Term | Meaning |
| --- | --- |
| Resource | A reservable asset (room, hall, vehicle, equipment). |
| Reservation | A time range during which a user holds a resource. |
| Approval | A decision (approve/reject) on a reservation that requires authorization. |
| Maintenance window | A period during which a resource is unavailable for booking. |
| Overlap | Two active reservations of the same resource whose time ranges intersect. |
| JWT | JSON Web Token used for stateless authentication. |

---

## 2. Overall description

### 2.1 Business problem
Without a central system, employees may reserve the same resource at the same time,
resources may be unavailable without notice, and administrators have no visibility into
how resources are used. FacilityBook solves this with a single source of truth, enforced
at the database level.

### 2.2 User roles
| Role | Responsibilities |
| --- | --- |
| **ADMIN** | Manage users, roles, resource categories, buildings, departments, and platform settings; can act as any approver. |
| **EMPLOYEE** | Search resources, create reservations, report damaged/unavailable resources, manage own bookings and notifications. |
| **FACILITY_MANAGER** | Manage facilities and resource availability, approve facility-routed reservations, schedule maintenance. |
| **DEPARTMENT_MANAGER** | Approve reservations routed to their department; view department reporting. |
| **RESOURCE_OPERATOR** | Manage resource operating schedules, availability, and maintenance. |

### 2.3 Assumptions and dependencies
- The application runs in a single timezone (`APP_TIMEZONE`); operating hours are local
  wall-clock times and API timestamps are UTC instants.
- Email/WebSocket delivery is out of scope; notifications are in-app.
- The system is delivered and started through Docker Compose.

---

## 3. Functional requirements

Each requirement is tagged `FR-<module>-<n>` and cross-referenced to the implemented
endpoint(s) and tests where useful.

### 3.1 Resource management
- **FR-RES-1** Authorized staff (ADMIN, FACILITY_MANAGER) can register a resource with
  name, description, category, building, floor, location detail, capacity (optional),
  features, and status.
- **FR-RES-2** Resources belong to exactly one category and optionally to a building
  (e.g. vehicles have no building).
- **FR-RES-3** Authorized staff (ADMIN, FACILITY_MANAGER, RESOURCE_OPERATOR) can define
  operating hours: one open/close window per resource per weekday.
- **FR-RES-4** Authorized staff can change a resource's operational status
  (`ACTIVE`/`INACTIVE`).
- **FR-RES-5** Any authenticated user can browse and search resources by category,
  building, capacity, status, and free-text name.
- **FR-RES-6** Any authenticated user can search for *available* resources for a given
  time window; results exclude inactive resources, resources under maintenance in the
  window, already-booked resources, and resources whose operating hours do not cover the
  window.
- **FR-RES-7** Resource categories define the approval policy: whether reservations
  require approval and, if so, which role approves them.

### 3.2 Reservation management
- **FR-BOOK-1** An authenticated user can create a reservation with resource, start time,
  end time, purpose, and optional notes.
- **FR-BOOK-2** The backend rejects a reservation whose end time is not after its start
  time, or whose start time is in the past.
- **FR-BOOK-3** The backend rejects a reservation that falls outside the resource's
  operating hours for that weekday.
- **FR-BOOK-4** The backend rejects a reservation that overlaps an active maintenance
  window for the resource.
- **FR-BOOK-5** Overlapping reservations for the same resource are prevented at the
  database level with a PostgreSQL exclusion constraint, so two simultaneous requests
  cannot both succeed. Back-to-back bookings (10:00–11:00 and 11:00–12:00) are allowed.
- **FR-BOOK-6** If the resource's category does not require approval, the reservation is
  confirmed automatically.
- **FR-BOOK-7** Users can view their own reservation history and upcoming bookings with
  pagination, sorting, and status filtering.
- **FR-BOOK-8** Staff (ADMIN, FACILITY_MANAGER, RESOURCE_OPERATOR, DEPARTMENT_MANAGER)
  can list reservations with filters.
- **FR-BOOK-9** A user can cancel their own reservation; staff can cancel any. The
  cancellation records the time and an optional reason and notifies affected parties.
- **FR-BOOK-10** A user can reschedule their own reservation; rescheduling re-runs all
  validation and, if the reservation required approval, returns it to
  `PENDING_APPROVAL`.
- **FR-BOOK-11** A reservation flagged by a conflicting maintenance window can be
  resolved by facility staff by cancelling it.

### 3.3 Approval workflow
- **FR-APP-1** Reservations are routed to the approver role configured on the resource
  category (facility manager or department manager); ADMIN can decide any pending
  request.
- **FR-APP-2** Approvers see only requests they are responsible for: department managers
  see their own department's requests, facility managers see facility-routed requests.
- **FR-APP-3** An approver can approve or reject with a reason. Rejection requires a
  reason.
- **FR-APP-4** An approver cannot decide their own reservation (self-approval is
  forbidden).
- **FR-APP-5** Every decision is stored in an approval history with actor, timestamp, and
  reason.
- **FR-APP-6** Approval is blocked if a maintenance window has since been scheduled over
  the reservation, so an approved booking can never collide with maintenance.
- **FR-APP-7** Approval/rejection notifies the requester.

### 3.4 Resource maintenance
- **FR-MNT-1** Any authenticated user (including EMPLOYEE) can report a damaged or
  unavailable resource; the report starts in status `REPORTED`.
- **FR-MNT-2** Facility staff (ADMIN, FACILITY_MANAGER, RESOURCE_OPERATOR) can schedule
  maintenance with a defined period and reason.
- **FR-MNT-3** Maintenance status transitions are tracked: `REPORTED → SCHEDULED →
  IN_PROGRESS → COMPLETED`, with `CANCELLED` as a terminal alternative.
- **FR-MNT-4** A resource under active maintenance (`SCHEDULED`/`IN_PROGRESS`) cannot be
  reserved during the affected period.
- **FR-MNT-5** Existing reservations that overlap a newly scheduled or started
  maintenance window are flagged (`needsResolution`) for authorized resolution.
- **FR-MNT-6** Completing maintenance restores the resource to service; completing a
  currently-running window clamps the end time to now so the record stays valid.
- **FR-MNT-7** Maintenance history is retained and listable by staff.

### 3.5 Notifications
- **FR-NOT-1** The system creates in-app notifications for: reservation confirmation,
  approval/rejection, cancellation, upcoming reservation reminders, and
  maintenance-related booking changes.
- **FR-NOT-2** A user can list their notifications, see an unread count, mark one as
  read, and mark all as read.
- **FR-NOT-3** A scheduled job creates at most one "upcoming reservation" reminder per
  reservation within the configured horizon.

### 3.6 Analytics and reporting
Available to ADMIN, FACILITY_MANAGER, DEPARTMENT_MANAGER, and RESOURCE_OPERATOR over a
selected period:
- **FR-REP-1** Total reservations and counts by status (confirmed, pending, cancelled,
  rejected, flagged).
- **FR-REP-2** Most frequently reserved resources with booked hours.
- **FR-REP-3** Resource utilization rates, measured against configured operating hours
  (capped at 100%).
- **FR-REP-4** Pending approvals and cancellations.
- **FR-REP-5** Maintenance frequency and downtime hours.
- **FR-REP-6** Reservations by department and peak time periods.

### 3.7 Authentication and user management
- **FR-AUTH-1** Users authenticate with email and password and receive a JWT.
- **FR-AUTH-2** The API is stateless; every protected request carries the JWT and the
  identity is derived from the security context.
- **FR-AUTH-3** ADMIN can list, create, and update users and list roles.

---

## 4. Non-functional requirements

| ID | Requirement |
| --- | --- |
| **NFR-1** | **Layered architecture.** Controllers, services, repositories, entities, DTOs, and security configuration are separate layers. |
| **NFR-2** | **DTO-only APIs.** Persistence entities are never exposed; requests and responses use dedicated DTOs. |
| **NFR-3** | **Backend validation.** All input is validated on the backend; errors are returned in a consistent `ApiError` shape (`timestamp, status, error, message, path`). |
| **NFR-4** | **Security.** Role-based and record-level authorization are enforced from the security context. Frontend guards are UX only. |
| **NFR-5** | **Concurrency safety.** Double-booking is prevented at the database level, not by check-then-insert. |
| **NFR-6** | **Migrations.** Schema is created and versioned solely through Flyway; Hibernate runs in `validate` mode. |
| **NFR-7** | **Query features.** List endpoints support pagination, sorting, searching, and filtering. |
| **NFR-8** | **Testing.** Automated tests cover business rules, authorization boundaries, and concurrency. |
| **NFR-9** | **Secrets.** Credentials and secrets come from environment variables; only `.env.example` is committed. |
| **NFR-10** | **Deployment.** The complete application starts with `docker compose up`. |
| **NFR-11** | **Time handling.** Consistent timezone handling; timestamps stored as `TIMESTAMPTZ` (UTC), operating hours interpreted in `APP_TIMEZONE`. |
| **NFR-12** | **API documentation.** The API is documented with Swagger/OpenAPI. |
| **NFR-13** | **Frontend states.** The UI handles loading, error, empty, and success states. |
| **NFR-14** | **State management.** Redux Toolkit holds shared/auth state; local UI state uses `useState`; server data is fetched and cached with RTK Query. |

---

## 5. Constraints

- Required technology stack (React + TypeScript, React Router, Redux Toolkit, Axios,
  React Hook Form, Zod; Java/Spring Boot, Spring Web, Spring Data JPA, Spring Security,
  JWT; PostgreSQL; Flyway; Swagger/OpenAPI; JUnit 5 + Mockito and a React testing tool;
  Docker Compose).
- One Flyway migration per table.
- The whole application must start through Docker Compose.

---

## 6. Acceptance criteria (from the brief) and status

| # | Criterion | Status | Evidence |
| --- | --- | --- | --- |
| 1 | An authorized administrator can create resources and define operating rules. | Met | `POST /api/resources`, `PUT /api/resources/{id}/operating-hours`; `SecurityAuthorizationTest.adminCanCreateResource...` |
| 2 | An employee can search resources and submit a reservation. | Met | `GET /api/resources/available`, `POST /api/reservations`; end-to-end smoke script |
| 3 | The backend rejects invalid time ranges and overlapping bookings. | Met | `chk_reservation_times`, `no_overlapping_reservations`; `ReservationServiceTest`, `ReservationConcurrencyTest` |
| 4 | Reservations requiring approval follow the correct approval workflow. | Met | `ApprovalController`, `ApprovalServiceTest` (8 tests) |
| 5 | Users can view their own reservation history. | Met | `GET /api/reservations/mine`; `SecurityAuthorizationTest.recordLevelAuthorization...` |
| 6 | Facility staff can schedule maintenance and block affected periods. | Met | `MaintenanceController`, `MaintenanceServiceTest`; smoke script conflict checks |
| 7 | The system provides useful reservation and utilization reports. | Met | `ReportController`, `ReportServiceTest` |
| 8 | Concurrent booking tests demonstrate that double booking is prevented. | Met | `ReservationConcurrencyTest` (DB exclusion + concurrent booking) |
