# NotifyFlow

```
 _   _       _   _  __      ______ _
| \ | |     | | (_)/ _|     |  ___| |
|  \| | ___ | |_ _| |_ _   _| |_  | | _____      __
| . ` |/ _ \| __| |  _| | | |  _| | |/ _ \ \ /\ / /
| |\  | (_) | |_| | | | |_| | |   | | (_) \ V  V /
\_| \_/\___/ \__|_|_|  \__, \_|   |_|\___/ \_/\_/
                         __/ |
                        |___/
```

> **Event-Driven Notification System** — Send real EMAIL via Gmail SMTP,
> with simulated SMS and IN_APP notifications through a production-grade
> Kafka pipeline with Redis deduplication, JWT security, and full async delivery tracking.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [How It Works](#how-it-works)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Kafka Topic Structure](#kafka-topic-structure)
- [Deduplication Strategy](#deduplication-strategy)
- [Quiet Hours Logic](#quiet-hours-logic)
- [Prerequisites](#prerequisites)
- [Quick Start (Docker)](#quick-start-docker)
- [Local Development](#local-development)
- [Running Tests](#running-tests)
- [Environment Variables](#environment-variables)
- [Project Structure](#project-structure)
- [CI/CD](#cicd)
- [License](#license)

---

## Overview

NotifyFlow is a **production-grade, event-driven notification system** built with Java 17 and Spring Boot 3.2. It provides a REST API for sending email, SMS, and in-app notifications through a fully asynchronous Kafka processing pipeline. EMAIL is sent via **real Gmail SMTP** (App Password authentication), while SMS and IN_APP channels currently use simulated delivery. The system includes Redis-based deduplication, MySQL persistence for audit trails, JWT-based stateless authentication, and is fully containerized with Docker Compose for one-command local deployment.

The system sends EMAIL via real Gmail SMTP with App Password authentication, while SMS and IN_APP channels remain simulated with configurable failure rates. All three channels share retry logic with dead letter topics and quiet hours support for suppressing low-priority notifications during user-defined windows.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT (REST API)                            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  JWT Bearer Token
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Security Filter Chain                     │
│              JwtAuthenticationFilter → SecurityContext              │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    NotificationController                           │
│          POST /send  GET /history  GET /status  PATCH /status       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     NotificationService                             │
│                                                                     │
│  ┌─────────────┐   ┌──────────────────┐   ┌────────────────────┐  │
│  │  Preference │   │  Deduplication   │   │   Notification     │  │
│  │   Service   │   │    Service       │   │    Producer        │  │
│  │             │   │                  │   │                    │  │
│  │ QuietHours  │   │  Redis SETNX     │   │  KafkaTemplate     │  │
│  │ Check       │   │  TTL: 10 min     │   │  .send()           │  │
│  └──────┬──────┘   └────────┬─────────┘   └────────┬───────────┘  │
│         │                   │                       │              │
└─────────┼───────────────────┼───────────────────────┼──────────────┘
          │                   │                       │
          ▼                   ▼                       ▼
   ┌─────────────┐   ┌──────────────────┐   ┌────────────────────────┐
   │    MySQL    │   │      Redis       │   │    Apache Kafka        │
   │             │   │                  │   │                        │
   │ users       │   │ dedup:{uid}:     │   │  notifyflow.email  (3) │
   │ notificatio │   │  {ch}:{hash}     │   │  notifyflow.sms    (3) │
   │ ns          │   │                  │   │  notifyflow.inapp  (3) │
   │ user_prefs  │   │ user-preferences │   │                        │
   │             │   │ notif-history    │   │  *.dlt  (Dead Letter)  │
   └─────────────┘   └──────────────────┘   └──────────┬─────────────┘
                                                         │
                                ┌────────────────────────┤
                                │                        │
                                ▼                        ▼
                     ┌──────────────────┐    ┌──────────────────────┐
                     │  EmailConsumer   │    │   SmsConsumer        │
                     │  SmsConsumer     │    │   InAppConsumer      │
                     │  InAppConsumer   │    │                      │
                     │                  │    │  Retry: 3x / 1s      │
                      │  Real Gmail SMTP │    │  DLT on exhaustion   │
                      │  Simulate send   │    │                      │
                     │  Update MySQL    │    │                      │
                     │  Ack offset      │    │  DltConsumer         │
                     └──────────────────┘    │  → Mark FAILED       │
                                              └──────────────────────┘
```

### Request Flow

1. **Client** sends an HTTP request with a JWT Bearer token
2. **Spring Security** validates the token via `JwtAuthenticationFilter`
3. **Controller** receives the request and delegates to the service layer
4. **NotificationService** orchestrates the pipeline:
   - Validates user existence in MySQL
   - Checks channel preferences (email/SMS/in-app enabled)
   - Enforces quiet hours for LOW priority notifications
   - Runs Redis SETNX deduplication check (10-minute window)
   - Persists the notification as PENDING in MySQL
   - Publishes a `NotificationEvent` to the appropriate Kafka topic
   - Returns `202 Accepted` with the notification ID
5. **Kafka Consumers** process the event asynchronously:
   - **EmailConsumer**: Sends real email via Gmail SMTP
   - **SmsConsumer / InAppConsumer**: Simulate delivery (200-500ms latency, configurable 10% failure rate)
   - All consumers update MySQL status to DELIVERED or FAILED
   - All consumers manually acknowledge the offset (at-least-once semantics)
6. **Dead Letter Topic Consumer** catches messages that exhausted retries and marks them as FAILED

---

## Tech Stack

| Technology        | Version  | Purpose                                  |
|-------------------|----------|------------------------------------------|
| Java              | 17       | Language / runtime                       |
| Spring Boot       | 3.2.5    | Application framework                    |
| Spring Security 6 | 6.x      | Authentication & authorization           |
| JWT (HS512)       | 0.12.6   | Stateless token auth via JJWT            |
| Apache Kafka      | 7.6.1    | Async message broker (3 topics × 3 partitions) |
| Redis             | 7.2      | Deduplication (SETNX) + caching (preferences, history) |
| MySQL             | 8.0      | Persistent storage (users, notifications, preferences) |
| Flyway            | -        | Database version control (V1/V2/V3)      |
| HikariCP          | -        | Connection pooling                       |
| Lombok            | -        | Boilerplate reduction                    |
| Swagger/OpenAPI 3 | 2.5.0    | API documentation at `/swagger-ui.html`  |
| Docker Compose    | 2.x      | Full local stack orchestration           |
| Maven             | 3.9+     | Build tool                               |
| JUnit 5 + Mockito | -        | Unit testing                             |
| JavaMailSender    | -        | Real email delivery via Gmail SMTP (JavaMail + Spring Mail abstraction) |
| JaCoCo            | 0.8.12   | Code coverage (70% minimum on service layer) |
| GitHub Actions    | -        | CI pipeline                              |

---

## Features

- ✅ **REST API** — Send EMAIL, SMS, IN_APP notifications via clean JSON endpoints
- ✅ **Gmail SMTP** — EMAIL sent via real Gmail SMTP with App Password auth; SMS and IN_APP remain simulated for now
- ✅ **Apache Kafka** — 3 topics × 3 partitions with async consumer processing
- ✅ **Redis Deduplication** — Atomic SETNX prevents duplicate sends within 10-minute TTL
- ✅ **Redis Caching** — User preferences (30 min TTL) and notification history (5 min TTL)
- ✅ **Real EMAIL via Gmail SMTP** — EmailConsumer sends real email; SMS and IN_APP remain simulated for now
- ✅ **Dead Letter Topics** — Failed messages routed to `*.dlt` after 3 retries
- ✅ **Spring Security 6 + JWT** — HS512-signed tokens, stateless session, BCrypt passwords
- ✅ **Quiet Hours** — LOW priority notifications suppressed during user-configured windows
- ✅ **Flyway Migrations** — Version-controlled schema with V1/V2/V3 migrations
- ✅ **Swagger UI** — Full OpenAPI 3 docs at `/swagger-ui.html` with JWT auth support
- ✅ **Docker Compose** — One-command local stack (MySQL + Redis + Zookeeper + Kafka + App)
- ✅ **JUnit 5 + Mockito** — Unit tests with 70%+ service layer coverage enforced by JaCoCo
- ✅ **GitHub Actions CI** — Automated build and test on every push and pull request
- ✅ **Actuator** — `/actuator/health` and `/actuator/info` for container health checks
- ✅ **HikariCP** — Production-grade connection pooling with configured timeouts
- ✅ **Overnight Quiet Hours** — Correctly handles windows crossing midnight (e.g. 22:00–08:00)

---

## How It Works

### Send Pipeline (Synchronous → Asynchronous)

```
HTTP POST /api/notifications/send
         │
         ├── 1. Validate user exists (MySQL lookup)
         ├── 2. Check channel is enabled (preferences)
         ├── 3. Check quiet hours (LOW priority only)
         ├── 4. Deduplication check (Redis SETNX)
         ├── 5. Save notification as PENDING (MySQL)
         ├── 6. Publish event to Kafka (fire-and-forget)
         └── 7. Return 202 Accepted with notification ID
                           │
                    Kafka Consumer
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    EmailConsumer    SmsConsumer      InAppConsumer
          │                 │                 │
          ├── Real SMTP  ├── Simulate send ├── Simulate send
          ├── Gmail send ├── 10% fail rate ├── 10% fail rate
          └── Update SQL  └── Update MySQL  └── Update MySQL
```

### Consumer Retry & Dead Letter Flow

```
Consumer receives message
         │
         ├── Success → ack offset → DELIVERED
         │
         └── Failure → DefaultErrorHandler
                  │
                  ├── Retry 1 (1s backoff)
                  ├── Retry 2 (1s backoff)
                  ├── Retry 3 (1s backoff)
                  │
                  └── Exhausted → DeadLetterPublishingRecoverer
                            │
                            └── *.dlt topic → DltConsumer
                                        │
                                        └── Mark FAILED in MySQL
```

---

## API Endpoints

### Auth

| Method | Endpoint               | Auth     | Description                        |
|--------|------------------------|----------|------------------------------------|
| POST   | `/api/auth/register`   | None     | Register new user, returns JWT     |
| POST   | `/api/auth/login`      | None     | Login with credentials, returns JWT|

### Notifications

| Method | Endpoint                          | Auth    | Description                              |
|--------|-----------------------------------|---------|------------------------------------------|
| POST   | `/api/notifications/send`         | JWT     | Send a notification (async via Kafka)    |
| GET    | `/api/notifications/{userId}/history` | JWT | Paginated notification history           |
| GET    | `/api/notifications/{id}/status`  | JWT     | Get single notification status           |
| PATCH  | `/api/notifications/{id}/status`  | ADMIN   | Manually update delivery status          |

### Preferences

| Method | Endpoint                      | Auth | Description                         |
|--------|-------------------------------|------|-------------------------------------|
| GET    | `/api/preferences/{userId}`   | JWT  | Get user notification preferences   |
| PUT    | `/api/preferences/{userId}`   | JWT  | Update channel toggles + quiet hours|

### Example Requests

**Register a user:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "secret123",
    "role": "USER"
  }'
```

**Send a notification:**
```bash
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "userId": 1,
    "channel": "EMAIL",
    "title": "Your order has shipped",
    "message": "Order #12345 is on its way!",
    "priority": "NORMAL"
  }'
```

---

## Database Schema

### `users` — Registered application users

| Column        | Type         | Constraints               |
|---------------|--------------|---------------------------|
| id            | BIGINT       | PK, AUTO_INCREMENT        |
| name          | VARCHAR(100) | NOT NULL                  |
| email         | VARCHAR(255) | NOT NULL, UNIQUE          |
| password_hash | VARCHAR(255) | NOT NULL (BCrypt hash)    |
| role          | VARCHAR(20)  | NOT NULL, DEFAULT 'USER'  |
| created_at    | DATETIME(6)  | NOT NULL, DEFAULT NOW()   |

### `notifications` — Central audit log for all notifications

| Column       | Type         | Constraints                     |
|--------------|--------------|----------------------------------|
| id           | BIGINT       | PK, AUTO_INCREMENT               |
| user_id      | BIGINT       | FK → users.id, NOT NULL          |
| channel      | VARCHAR(20)  | NOT NULL (EMAIL/SMS/IN_APP)      |
| title        | VARCHAR(255) | NOT NULL                         |
| message      | TEXT         | NOT NULL                         |
| status       | VARCHAR(20)  | NOT NULL, DEFAULT 'PENDING'      |
| priority     | VARCHAR(10)  | NOT NULL, DEFAULT 'NORMAL'       |
| kafka_offset | BIGINT       | NULLABLE (for traceability)      |
| retry_count  | INT          | NOT NULL, DEFAULT 0              |
| created_at   | DATETIME(6)  | NOT NULL, DEFAULT NOW()          |
| delivered_at | DATETIME(6)  | NULLABLE                         |

### `user_preferences` — Per-user notification settings (1:1 with users)

| Column             | Type        | Constraints                     |
|--------------------|-------------|----------------------------------|
| id                 | BIGINT      | PK, AUTO_INCREMENT               |
| user_id            | BIGINT      | FK → users.id, UNIQUE, NOT NULL  |
| email_enabled      | BOOLEAN     | NOT NULL, DEFAULT TRUE           |
| sms_enabled        | BOOLEAN     | NOT NULL, DEFAULT TRUE           |
| in_app_enabled     | BOOLEAN     | NOT NULL, DEFAULT TRUE           |
| quiet_hours_start  | TIME        | NULLABLE (e.g. 22:00)            |
| quiet_hours_end    | TIME        | NULLABLE (e.g. 08:00)            |
| updated_at         | DATETIME(6) | NOT NULL, DEFAULT NOW()          |

---

## Kafka Topic Structure

```
notifyflow.email     ─── 3 partitions ── replication: 1
notifyflow.sms       ─── 3 partitions ── replication: 1
notifyflow.inapp     ─── 3 partitions ── replication: 1

notifyflow.email.dlt ─── 1 partition  ── Dead Letter (retry exhausted)
notifyflow.sms.dlt   ─── 1 partition  ── Dead Letter (retry exhausted)
notifyflow.inapp.dlt ─── 1 partition  ── Dead Letter (retry exhausted)
```

- **Partitioning key:** `userId` — all notifications for the same user land on the same partition, preserving per-user ordering guarantees.
- **Consumer retry policy:** 3 attempts with 1-second fixed backoff.
- **Dead letter handling:** After exhaustion, `DeadLetterPublishingRecoverer` routes the message to the `.dlt` topic and `DltConsumer` marks the notification as `FAILED` in MySQL.

---

## Deduplication Strategy

When a notification request arrives, NotifyFlow computes an **MD5 fingerprint** of the `title|message` content and attempts a Redis **`SETNX`** (set-if-not-exists) on the key:

```
dedup:{userId}:{channel}:{md5(title|message)}
```

with a **10-minute TTL**:

| SETNX Result      | Meaning                  | HTTP Response |
|-------------------|--------------------------|---------------|
| `true` (set)      | New notification         | Proceed with send |
| `false` (exists)  | Duplicate within window  | `409 Conflict` with remaining TTL |
| `null` (Redis down)| Cannot check           | Proceed (fail-open for availability) |

This is an **atomic Redis operation** — no race conditions under concurrent load. The TTL is included in the 409 error response so the client knows exactly when to retry.

---

## Quiet Hours Logic

Users can configure a quiet hours window (e.g., 22:00–08:00). LOW priority notifications submitted during this window receive `HTTP 429 Too Many Requests`.

The logic correctly handles:

- **Same-day windows** (09:00–17:00): Active when `start <= now < end`
- **Overnight windows** (22:00–08:00): Active when `now >= start OR now < end`

HIGH and NORMAL priority notifications pass through regardless of quiet hours.

---

## Prerequisites

| Tool             | Version | Install                              |
|------------------|---------|--------------------------------------|
| Java JDK         | 17+     | https://adoptium.net                 |
| Docker           | 24+     | https://docs.docker.com/get-docker   |
| Docker Compose   | 2.x     | Included with Docker Desktop         |
| Maven            | 3.9+    | https://maven.apache.org/install.html|

---

## Quick Start (Docker)

```bash
# 1 — Clone the repository
git clone https://github.com/YOUR_USERNAME/notifyflow.git
cd notifyflow

# 2 — Copy environment variables and edit credentials
cp .env.example .env

# 3 — Start the full stack (MySQL + Redis + Kafka + App)
docker-compose up --build
```

The API will be available at `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

> **First run takes ~2 minutes** — Maven downloads dependencies, Docker pulls images, and Flyway runs migrations.

---

## Local Development

```bash
# Start only infrastructure services
docker-compose up mysql redis zookeeper kafka -d

# Run the application locally
mvn spring-boot:run

# Or with test profile
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

---

## Running Tests

```bash
# Run all unit tests
mvn test

# Run tests with coverage report
mvn clean verify

# View coverage report
# Open target/site/jacoco/index.html in your browser
```

JaCoCo enforces **70% line coverage** on the `com.notifyflow.service` package. The build fails if coverage drops below this threshold.

---

## Environment Variables

| Variable              | Description                          | Default (dev)            |
|-----------------------|--------------------------------------|--------------------------|
| `MYSQL_DATABASE`      | MySQL database name                  | `notifyflow_db`          |
| `MYSQL_USER`          | MySQL username                       | `notifyflow_user`        |
| `MYSQL_PASSWORD`      | MySQL password                       | *(change me)*            |
| `MYSQL_ROOT_PASSWORD` | MySQL root password                  | *(change me)*            |
| `REDIS_PASSWORD`      | Redis auth password                  | *(change me)*            |
| `JWT_SECRET`          | Base64 secret ≥ 64 bytes for HS512   | *(change me)*            |
| `JWT_EXPIRATION_MS`   | Token expiry in milliseconds         | `86400000` (24h)         |
| `MAIL_USERNAME`       | Gmail email address for SMTP auth    | *(must set)*             |
| `MAIL_APP_PASSWORD`   | Gmail App Password (16-letter code)  | *(must set)*             |

Generate a secure JWT secret:
```bash
openssl rand -base64 64
```

### Gmail App Password Setup

Email sending requires a Gmail App Password (not your regular account password).

**Step-by-step:**

1. Go to https://myaccount.google.com/security
2. Turn on **2-Step Verification** if not already enabled
3. Go to https://myaccount.google.com/apppasswords
4. Select **Mail** as the app and **Windows Computer** as the device
5. Click **Generate** — you will receive a **16-letter code** (e.g. `abcd efgh ijkl mnop`)
6. Copy the code — this is your `MAIL_APP_PASSWORD`. You will not see it again.

**Setting the environment variables:**

_(a) Windows PowerShell (local development):_
```powershell
$env:MAIL_USERNAME="your.email@gmail.com"
$env:MAIL_APP_PASSWORD="abcd efgh ijkl mnop"
mvn spring-boot:run -pl notify-backend
```

_(b) Docker Compose — edit `.env` in the repo root:_
```env
# Add these lines to .env
MAIL_USERNAME=your.email@gmail.com
MAIL_APP_PASSWORD=abcd efgh ijkl mnop
```
The `docker-compose.yml` already forwards `MAIL_USERNAME` and `MAIL_APP_PASSWORD` to the app container.

> **Note:** If the App Password contains spaces (e.g. `abcd efgh ijkl mnop`), pass it exactly as shown — Spring Boot strips whitespace from property values automatically.

---

## Project Structure

```
notifyflow/
├── src/main/java/com/notifyflow/
│   ├── config/           # SecurityConfig, KafkaConfig, RedisConfig, SwaggerConfig
│   ├── controller/       # AuthController, NotificationController, PreferenceController
│   ├── dto/              # Request/Response DTOs, PagedResponseDTO, ErrorResponseDTO
│   ├── exception/        # Custom exceptions + GlobalExceptionHandler
│   ├── kafka/
│   │   ├── consumer/     # EmailConsumer, SmsConsumer, InAppConsumer, DltConsumer
│   │   ├── event/        # NotificationEvent (Kafka message payload)
│   │   └── producer/     # NotificationProducer
│   ├── model/
│   │   ├── entity/       # UserEntity, NotificationEntity, UserPreferenceEntity
│   │   └── enums/        # NotificationChannel, NotificationStatus, NotificationPriority, UserRole
│   ├── repository/       # UserRepository, NotificationRepository, PreferenceRepository
│   ├── service/          # AuthService, NotificationService, PreferenceService, DeduplicationService, EmailSenderService
│   └── util/             # JwtUtil, JwtAuthenticationFilter, RedisKeyUtil
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/     # V1__create_users.sql, V2__create_notifications.sql, V3__create_preferences.sql
│
├── src/test/java/com/notifyflow/
│   ├── kafka/consumer/   # EmailConsumerTest
│   ├── kafka/producer/   # NotificationProducerTest
│   ├── service/          # NotificationServiceTest, DeduplicationServiceTest
│   └── util/             # JwtUtilTest
│
├── src/test/resources/
│   └── application-test.yml
│
├── .github/workflows/        # ci.yml — Build, test, coverage, Docker build
├── docker-compose.yml        # Full stack: MySQL + Redis + ZK + Kafka + App
├── pom.xml                   # Maven build with JaCoCo, Surefire, Spring Boot
├── Dockerfile                # Multi-stage build: Maven builder → JRE runtime
├── .env.example              # Environment variable template
├── .gitignore
└── README.md
```

---

## CI/CD

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push and pull request:

1. **Checkout** the repository
2. **Setup Java 17** with Temurin distribution
3. **Start MySQL 8** service container
4. **Start Redis** service container
5. **Build and test** with Maven:
   ```bash
   mvn clean verify
   ```
6. **JaCoCo coverage check** — fails build if service package coverage < 70%
7. **Docker build** — validates the Dockerfile builds successfully

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
