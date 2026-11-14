# Screenshots

The [user guide](../USER_GUIDE.md) and the [README](../../README.md) reference these
images. Capture them from a running stack (`docker compose up --build -d` plus
`cd frontend && npm run dev`) and save them here with exactly these file names.

| File | Screen | What to show |
| --- | --- | --- |
| `01-login.png` | Login | The login form. |
| `02-resource-search.png` | Resources | An availability search with filters and results. |
| `03-create-reservation.png` | Reserve | The reservation form with purpose and time window. |
| `04-my-reservations.png` | My reservations | A list showing confirmed and pending bookings. |
| `05-report-damage.png` | Maintenance | An employee submitting a damage report. |
| `06-notifications.png` | Notifications | A mix of read and unread notifications. |
| `07-approvals.png` | Approvals | The pending queue with an approve/reject action. |
| `08-manage-resources.png` | Resource admin | Creating/editing a resource and its operating hours. |
| `09-maintenance.png` | Maintenance | A maintenance window and a flagged conflicting reservation. |
| `10-admin.png` | Admin | The user/role management screen. |
| `11-reports.png` | Reports | The summary, utilization, and department charts. |

Tips:
- Use a fresh database (`docker compose down -v && docker compose up -d`) so screenshots
  show a clean calendar.
- Use a desktop viewport (e.g. 1440×900) for consistent framing.
- Do not include real credentials or secrets in any screenshot.
