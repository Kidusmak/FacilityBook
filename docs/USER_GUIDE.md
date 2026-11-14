# User Guide — FacilityBook

This guide walks through the main workflows for each role. Start the application first
(see the [README](../README.md#3-setup-and-run)); the frontend runs at
`http://localhost:5173` and the API at `http://localhost:8080/api`.

> Screenshots referenced below live in [`screenshots/`](screenshots/). See that folder's
> README for the list of images to capture.

---

## 1. Getting started

1. Open `http://localhost:5173`.
2. Log in with a demo account (see the table below). The password for all demo accounts
   is `Password123!`.

| Role | Email |
| --- | --- |
| Admin | `admin@facilitybook.local` |
| Employee | `employee@facilitybook.local` |
| Facility Manager | `facility@facilitybook.local` |
| Department Manager | `deptmanager@facilitybook.local` |
| Resource Operator | `operator@facilitybook.local` |

3. The navigation menu is role-aware: it only shows the sections your role can use, and
   the backend independently enforces every permission.

![Login](screenshots/01-login.png)

---

## 2. Employee workflows

### 2.1 Find an available resource
1. Open **Resources**.
2. Choose a **date**, **start time**, and **end time**, and optionally filter by category,
   building, or capacity.
3. The list shows only resources that are free for the whole window and open during it.

![Search availability](screenshots/02-resource-search.png)

### 2.2 Make a reservation
1. From a resource, choose **Reserve**.
2. Enter the purpose and optional notes and confirm the time window.
3. Depending on the category:
   - Meeting rooms, training rooms, and equipment are **confirmed immediately**.
   - Conference halls need **facility manager** approval; vehicles need **department
     manager** approval. These are created as **Pending approval**.

![Create reservation](screenshots/03-create-reservation.png)

If the slot is already taken or the resource is closed, the app shows a clear error
(`409` for a clash, `400` for an invalid window or outside operating hours).

### 2.3 View and manage your bookings
- **My reservations** lists your bookings with their status. You can filter by status.
- **Reschedule** a booking to move it to a new window (validation runs again; a booking
  that required approval goes back to *Pending approval*).
- **Cancel** a booking; you may give a reason.

![My reservations](screenshots/04-my-reservations.png)

### 2.4 Report a damaged resource
1. Open **Maintenance**.
2. Choose the resource, describe the problem, and set the affected period.
3. Submit. The report is created as **Reported** for staff to triage. (Only staff can
   schedule or progress maintenance.)

![Report damage](screenshots/05-report-damage.png)

### 2.5 Notifications
Open **Notifications** to see confirmations, approval decisions, cancellations, upcoming
reminders, and maintenance-related changes. Mark items read individually or all at once.

![Notifications](screenshots/06-notifications.png)

---

## 3. Approver workflows (Facility Manager / Department Manager / Admin)

1. Open **Approvals** to see the pending queue.
   - Department managers see only their own department's requests.
   - Facility managers see facility-routed requests.
   - Admins see everything.
2. Select a request and **Approve** or **Reject** (a reason is required to reject).
3. The requester is notified and the decision is stored in the approval history.

Rules enforced by the backend:
- You cannot approve a request routed to a different approver role.
- A department manager cannot approve outside their department.
- You cannot approve your own request.
- If maintenance now overlaps the request, approval is blocked (`409`).

![Approval queue](screenshots/07-approvals.png)

---

## 4. Facility Manager / Resource Operator workflows

### 4.1 Manage resources
1. Open **Admin → Resources** (or the Resources screen with management controls).
2. Create or edit a resource: name, description, category, building, floor, location,
   capacity, features, and status.
3. Define **operating hours**: set an open and close time for each weekday the resource is
   available.

![Manage resources](screenshots/08-manage-resources.png)

### 4.2 Schedule maintenance
1. Open **Maintenance** and create a maintenance window for a resource with a reason.
2. Any active reservation that overlaps the window is **flagged for resolution**.
3. Progress the record: **Scheduled → In progress → Completed**. Completing it returns the
   resource to service.

### 4.3 Resolve a maintenance conflict
1. On a flagged reservation, choose **Resolve conflict** and cancel it.
2. The requester is notified and the flag is cleared.

![Maintenance](screenshots/09-maintenance.png)

---

## 5. Admin workflows

### 5.1 Manage reference data
- **Resource categories** — set whether a category requires approval and which role
  approves it.
- **Buildings** and **Departments** — maintain location and organisational data.

### 5.2 Manage users and roles
1. Open **Admin → Users**.
2. Create or update users and assign a role and department.
3. View the available roles.

![Admin](screenshots/10-admin.png)

---

## 6. Reports

Open **Reports** (Admin, Facility Manager, Department Manager, Resource Operator). Choose a
period to see:
- total reservations and a breakdown by status,
- most frequently reserved resources with booked hours,
- **utilization rate** — booked hours ÷ configured operating hours for the period, capped
  at 100% (resources without operating hours are measured against the whole period),
- maintenance frequency and downtime hours,
- reservations by department and peak time periods.

![Reports](screenshots/11-reports.png)

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `401` on every request | Missing/expired token | Log in again. |
| `403` on an action | Your role is not permitted | Check the role → capability table in `docs/ARCHITECTURE.md`. |
| `409` when booking | The slot overlaps another booking or maintenance | Pick a different slot or resource. |
| `400` when booking | End before start, start in the past, or outside operating hours | Adjust the time window. |
| Frontend shows a connection error | API not running | `docker compose up -d` and check `http://localhost:8080/actuator/health`. |
