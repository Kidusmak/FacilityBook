# Individual Contribution Report — FacilityBook

> **How to use this document.** The brief requires an individual contribution report from
> every intern. Copy the template in section 2 once per team member, replace the
> placeholders with real names and details, and delete this note. Keep the reports
> honest and specific — reference files, endpoints, and tests you actually wrote.

## 1. Team overview

| Member | Primary area | End-to-end feature owned |
| --- | --- | --- |
| _<Full name 1>_ | _e.g. Backend / database_ | _e.g. Reservation booking + overlap prevention_ |
| _<Full name 2>_ | _e.g. Frontend_ | _e.g. Approval workflow UI_ |
| _<Full name 3>_ | _e.g. Backend / DevOps_ | _e.g. Maintenance + Docker_ |
| _<Full name 4 (if any)>_ | _e.g. Frontend / docs_ | _e.g. Reports + notifications_ |

> The brief recommends 3–4 interns and requires that **every intern owns at least one
> end-to-end feature touching UI, API, business logic, and database**.

---

## 2. Individual report template

### Member: _<Full name>_

**Role / primary area:** _<e.g. Backend developer>_

**End-to-end feature(s) owned:**
- _<Feature name>_ — from the UI through the API, business logic, and database.
  - UI: _<page/component files>_
  - API: _<endpoints>_
  - Business logic: _<service files and rules>_
  - Database: _<tables/migrations/constraints>_

**What I implemented (specific deliverables):**
- _<e.g. Designed the `reservations` table and the `no_overlapping_reservations` exclusion
  constraint in `V8__create_reservations_table.sql`.>_
- _<e.g. Implemented `ReservationService` create/reschedule/cancel with validation and row
  locking.>_
- _<e.g. Built the "My reservations" page with RTK Query and React Hook Form + Zod.>_

**Key technical decisions I made and why:**
- _<Decision>_ — _<rationale>_.

**Tests I wrote:**
- _<Test class / file and what it proves>_.

**Challenges and how I solved them:**
- _<Challenge>_ → _<solution>_.

**What I learned:**
- _<Skill/concept>_.

**Time spent / period:** _<e.g. ~3 weeks>_.

**Support provided to teammates / reviewed areas:**
- _<e.g. reviewed the approval service, helped debug Docker networking>_.

---

## 3. Shared responsibilities (all members)

Regardless of individual ownership, every member should be able to explain:

- The overall architecture (React SPA → Spring Boot API → PostgreSQL).
- The security model (JWT, role-based + record-level authorization).
- The concurrency guarantee (PostgreSQL exclusion constraint + row locks).
- The approval workflow and how routing is configured per category.
- How the application is built and run with Docker Compose.
- How to run the backend and frontend test suites.

## 4. Sign-off

| Member | Signature | Date |
| --- | --- | --- |
| _<Full name 1>_ | | |
| _<Full name 2>_ | | |
| _<Full name 3>_ | | |
| _<Full name 4>_ | | |
