# Test Report — FacilityBook

**Date:** 2026-11-14
**Backend result:** `mvn -B clean test` → **33 tests, 0 failures, 0 errors** (BUILD SUCCESS)
**Frontend result:** `npm test` → **17 tests, 0 failures** across 4 files
**End-to-end smoke:** 66/66 API checks pass against a freshly built Docker stack

---

## 1. How to run the tests

### Backend (JUnit 5 + Mockito; integration tests use Testcontainers)

```bash
mvn -B clean test
```

- Unit tests (`service/*`) use Mockito only.
- Integration tests (`ReservationConcurrencyTest`, `SecurityAuthorizationTest`) start a
  throwaway `postgres:16-alpine` container with Testcontainers, run the real Flyway
  migrations, and exercise the running Spring context. **Docker must be running.**
- Always run `clean test` so the full context (repositories + migrations) is rebuilt.

### Frontend (Vitest + React Testing Library)

```bash
cd frontend
npm test          # single run
npm run test:watch
```

---

## 2. Backend test inventory (33 tests)

### 2.1 `ReservationConcurrencyTest` — 2 tests (integration, Testcontainers)
The critical concurrency scenario from the brief.

| Test | What it proves |
| --- | --- |
| `serviceCreatesExactlyOneOfTwoConcurrentOverlappingReservations` | Two threads submit overlapping bookings for the same resource; exactly one succeeds. |
| `databaseExclusionConstraintRejectsOverlappingInserts` | A direct overlapping insert is rejected by the database exclusion constraint (`23P01`). |

### 2.2 `SecurityAuthorizationTest` — 6 tests (integration, Testcontainers)

| Test | What it proves |
| --- | --- |
| `unauthenticatedRequestGetsConsistentJson401` | Unauthenticated access returns a JSON `401` body, not an HTML page. |
| `employeeIsForbiddenFromStaffOnlyEndpoints` | An employee gets `403` on staff-only endpoints. |
| `employeeCanReportDamageButCannotScheduleMaintenance` | Employees may `POST /api/maintenance` as `REPORTED` (`201`) but not as `SCHEDULED` (`403`). |
| `facilityManagerCanReadReportsButOnlyAdminManagesUsers` | Reports are readable by a facility manager; user management is ADMIN-only. |
| `adminCanCreateResourceAndEmployeeCannotResolveConflicts` | Admin creates a resource (`201`); employees cannot resolve conflicts (`403`). |
| `recordLevelAuthorizationHidesReservationsFromOtherEmployees` | A user cannot read another user's reservation (`403`). |

### 2.3 `ApprovalServiceTest` — 8 tests (unit)

| Test | Business rule |
| --- | --- |
| `approvesPendingReservationWhenRolesMatch` | Correct approver confirms the booking. |
| `rejectsApprovalWhenFacilityManagerIsNotTheConfiguredApprover` | Role must match the category policy. |
| `departmentManagerCannotApproveAnotherDepartment` | Department-scoped record authorization. |
| `approverCannotApproveOwnReservation` | Self-approval is forbidden. |
| `rejectionRequiresAReason` | A rejection must carry a reason. |
| `rejectionStoresReasonAndNotifiesRequester` | Rejection persists the reason and notifies. |
| `cannotDecideReservationThatIsNotPending` | Only `PENDING_APPROVAL` can be decided. |
| `approvalIsBlockedWhenMaintenanceNowOverlapsTheReservation` | Approval is blocked if maintenance now overlaps. |

### 2.4 `MaintenanceServiceTest` — 7 tests (unit)

| Test | Business rule |
| --- | --- |
| `createsReportedMaintenanceAndFlagsOverlappingReservation` | New maintenance flags conflicting reservations. |
| `rejectsCreateWhenEndIsNotAfterStart` | Time-range validation. |
| `employeeCanReportDamagedResourceButCannotScheduleMaintenance` | Employee report vs staff schedule. |
| `rejectsTransitionOutOfTerminalState` | Terminal states cannot transition. |
| `completingRunningMaintenanceClampsEndToNow` | Completing a running window clamps the end time. |
| `completingFutureMaintenanceKeepsValidWindow` | Completing a future window keeps a valid range. |
| `movingToInProgressFlagsOverlappingReservations` | Starting maintenance flags overlaps. |

### 2.5 `ReservationServiceTest` — 7 tests (unit)

| Test | Business rule |
| --- | --- |
| `createsConfirmedReservationWhenCategoryDoesNotRequireApproval` | Auto-confirmation. |
| `routesRestrictedCategoryToConfiguredApprover` | Restricted categories go to the right approver. |
| `rejectsOverlappingReservationBeforeInserting` | Overlap rejected pre-insert. |
| `rejectsEndTimeBeforeStartTime` | Start-before-end validation. |
| `rejectsStartTimeInThePast` | No bookings in the past. |
| `preventsAnotherEmployeeFromCancellingSomeonesReservation` | Record-level cancel authorization. |
| `resolvesMaintenanceConflictByCancellingReservation` | Conflict resolution clears the flag. |

### 2.6 `ReportServiceTest` — 3 tests (unit)

| Test | Business rule |
| --- | --- |
| `utilizationIsMeasuredAgainstConfiguredOperatingHours` | Utilization uses configured operating hours. |
| `resourcesWithoutOperatingHoursUseTheWholePeriod` | Fallback to the whole period when no hours exist. |
| `rejectsReversedReportPeriod` | `to` must be after `from`. |

---

## 3. Frontend test inventory (17 tests / 4 files)

| File | Tests | Coverage |
| --- | --- | --- |
| `src/utils/format.test.ts` | 5 | Status/role label formatting, hour formatting, `LocalTime` and ISO instant formatting. |
| `src/utils/validation.test.ts` | 7 | Zod schemas: purpose required, end-after-start, future start, valid window, inverted reschedule window, non-positive capacity, maintenance reason/window. |
| `src/features/auth/RequireAuth.test.tsx` | 2 | Anonymous visitors redirect to login; authenticated users reach protected pages. |
| `src/pages/LoginPage.test.tsx` | 3 | Empty-submit validation messages, malformed email rejection, demo-account autofill. |

---

## 4. End-to-end smoke verification

A PowerShell script exercises the live API against the Docker stack and asserts **66**
checks, including:

- all five demo accounts log in;
- resource creation is ADMIN/Facility-Manager-only; an employee gets `403`;
- availability search; a reservation is created and auto-confirmed;
- an overlapping booking is rejected (`409`); back-to-back bookings are allowed;
- invalid range and outside-operating-hours are rejected (`400`);
- a restricted resource is created `PENDING_APPROVAL` and routed to the correct approver;
- department manager cannot approve their own request; facility manager cannot decide a
  department-routed request; admin can decide any;
- record-level reservation access (`403` for another user's booking);
- notifications list, unread count, mark one, mark all;
- maintenance scheduling flags a conflicting reservation; the reservation is blocked
  (`409`); staff resolves the conflict; completing maintenance records the timestamp;
- employees can report damage (`201`, status `REPORTED`) but cannot schedule (`403`) or
  list all maintenance (`403`);
- an unknown sort property returns a consistent `400` `ApiError`;
- reports (summary, resource utilization, department, peak times) return `200`;
- OpenAPI JSON and Swagger UI are public; user listing is ADMIN-only; the resource
  operator can update availability but cannot create resources.

---

## 5. Coverage against acceptance criteria

| Criterion | Backing tests |
| --- | --- |
| Admin creates resources and operating rules | `SecurityAuthorizationTest.adminCanCreateResource...`, smoke |
| Employee searches and reserves | `ReservationServiceTest`, smoke |
| Invalid ranges and overlaps rejected | `ReservationServiceTest`, `chk_reservation_times`, smoke |
| Correct approval workflow | `ApprovalServiceTest` (8) |
| Own reservation history | `SecurityAuthorizationTest.recordLevelAuthorization...`, smoke |
| Maintenance blocks periods | `MaintenanceServiceTest` (7), smoke |
| Reports and utilization | `ReportServiceTest` (3), smoke |
| Concurrent booking prevented | `ReservationConcurrencyTest` (2) |

---

## 6. Notes and known gaps

- Integration tests require Docker; they are annotated `disabledWithoutDocker = true` so
  the suite still compiles and skips gracefully without it.
- The frontend suite covers validation, formatting, routing guards, and the login form.
  Full page workflows (booking, approvals, maintenance) are validated by the end-to-end
  smoke script rather than component tests.
- Testcontainers emits an expected `deadlock detected` / `exclusion constraint` warning
  during the concurrency test — this is the database correctly rejecting the losing
  transaction, not a test failure.
