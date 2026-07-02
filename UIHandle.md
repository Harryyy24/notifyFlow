# NotifyFlow — UI User Guide

## Access

| Service        | URL                                   |
|----------------|---------------------------------------|
| Frontend (UI)  | http://localhost:5173                  |
| Backend API    | http://localhost:8080                  |
| Swagger Docs   | http://localhost:8080/swagger-ui.html  |

---

## 1. Registration & Login

### Register a new account
1. Go to http://localhost:5173/register
2. Fill in **Full name**, **Email**, **Password**
3. Click **Create account**
4. You are automatically logged in and redirected to the dashboard
5. All new accounts are created with the **USER** role (Admin is not available via registration)

### Login
1. Go to http://localhost:5173/login
2. Enter your **Email** and **Password**
3. Click **Sign in**

### Pre-seeded admin accounts

| Email                          | Password      | Role  |
|--------------------------------|---------------|-------|
| admin@notifyflow.com           | admin123      | ADMIN |
| harishthube4455@gmail.com      | Harry@2004    | ADMIN |

---

## 2. Dashboard

After logging in, the dashboard shows:

- **Stats cards** — Total notifications, delivered, pending, failed counts
- **Delivery rate** — Percentage of successful deliveries
- **Channel breakdown** — Email / SMS / In-app notification counts
- Recent activity overview

---

## 3. Sending Notifications

1. Go to the **Send** page
2. Enter the **User ID** of the recipient (find your ID in the table below)
3. Choose a **Channel** — Email, SMS, or In-App
4. Enter a **Title** and **Message**
5. Set a **Priority** — High, Normal, or Low
6. Click **Send Notification**
7. The notification is queued via Kafka and processed asynchronously
8. Status updates to **DELIVERED** or **FAILED** within seconds

### Registered User IDs

| ID | Name | Email | Role |
|----|------|-------|------|
| 1  | NotifyFlow Admin | admin@notifyflow.com | ADMIN |
| 5  | Harish Thube | harishthube4455@gmail.com | ADMIN |
| 6  | Harish Thube | harryclaude18@gmail.com | USER |

---

## 4. History

1. Go to the **History** page
2. View all past notifications with their current status
3. Filter by channel, status, or date
4. Each row shows: ID, channel, title, status, priority, created time, delivered time

---

## 5. Preferences

1. Go to the **Preferences** page for any user
2. Toggle channels on/off — Email, SMS, In-App
3. Set **Quiet Hours** (start/end time) — LOW priority notifications are suppressed during this window
4. Quiet hours correctly handle overnight windows (e.g., 22:00–08:00)

---

## 6. Admin Panel (ADMIN role only)

Available to users with `ADMIN` role:

1. **Manual status update** — Change any notification's status (PENDING → DELIVERED/FAILED)
2. **User management** — View all registered users

---

## 7. Testing the API via Swagger

1. Open http://localhost:8080/swagger-ui.html
2. Use `/api/auth/login` to get a JWT token
3. Click **Authorize** and paste the token as `Bearer <token>`
4. Try any endpoint directly from the browser

### Quick curl examples

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@test.com","password":"test123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@notifyflow.com","password":"admin123"}'

# Send notification (replace TOKEN with your JWT)
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"userId":1,"channel":"EMAIL","title":"Hello","message":"Test message","priority":"NORMAL"}'
```

---

## 8. Troubleshooting

- **502 Bad Gateway on register/login** — Backend is still starting up. Wait ~30s and refresh.
- **401 Unauthorized** — Token expired or invalid. Login again.
- **409 Conflict on send** — Duplicate notification detected within 10-minute dedup window.
- **429 Too Many Requests** — LOW priority notification sent during quiet hours.
