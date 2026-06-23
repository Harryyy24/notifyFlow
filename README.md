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

> **Event-Driven Notification System** — Send EMAIL, SMS, and IN_APP
> notifications through a production-grade Kafka pipeline with Redis
> deduplication, JWT security, and full async delivery tracking.

---

## Badges

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen?style=flat-square&logo=springboot)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-7.2-red?style=flat-square&logo=redis)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![JWT](https://img.shields.io/badge/JWT-HS512-purple?style=flat-square&logo=jsonwebtokens)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)
![CI](https://img.shields.io/github/actions/workflow/status/YOUR_USERNAME/notifyflow/ci.yml?style=flat-square&label=CI)

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
                    │  Simulate send   │    │  DLT on exhaustion   │
                    │  Update MySQL    │    │                      │
                    │  Ack offset      │    │  DltConsumer         │
                    └──────────────────┘    │  → Mark FAILED       │
                                            └──────────────────────┘
```

---

## Features

- ✅ **REST API** — Send EMAIL, SMS, IN_APP notifications via clean JSON endpoints
- ✅ **Apache Kafka** — 3 topics × 3 partitions with async consumer processing
- ✅ **Redis Deduplication** — Atomic SETNX prevents duplicate sends within 10-minute TTL
- ✅ **Redis Caching** — User preferences (30 min TTL) and notification history (5 min TTL)
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

## Prerequisites

| Tool        | Version  | Install                              |
|-------------|----------|--------------------------------------|
| Java JDK    | 17+      | https://adoptium.net                 |
| Docker      | 24+      | https://docs.docker.com/get-docker   |
| Docker Compose | 2.x   | Included with Docker Desktop         |
| Maven       | 3.9+     | https://maven.apache.org/install.html|

---

## Quick Start

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

> **First run takes ~2 minutes** — Maven downloads dependencies,
> Docker pulls images, and Flyway runs migrations.

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

Generate a secure JWT secret:
```bash
openssl rand -base64 64
```

---

## How Deduplication Works

When a notification request arrives, NotifyFlow computes an MD5 fingerprint
of the `title + message` content and attempts a Redis `SETNX` (set-if-not-exists)
on the key `dedup:{userId}:{channel}:{md5hash}` with a 10-minute TTL.
If the key already exists (set returns false), the request is rejected with
`HTTP 409 Conflict` and the remaining TTL in seconds is included in the
error message so the client knows exactly when to retry.
This is an atomic Redis operation — no race conditions under concurrent load.

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

**Partitioning key:** `userId` — all notifications for the same user
land on the same partition, preserving per-user ordering guarantees.

**Consumer retry policy:** 3 attempts with 1-second fixed backoff.
After exhaustion, `DeadLetterPublishingRecoverer` routes the message
to the `.dlt` topic and `DltConsumer` marks the notification as `FAILED`.

---

## Running Tests

```bash
# Run all unit tests
mvn test

# Run tests with coverage report
mvn clean verify

# View coverage report (opens in browser)
open target/site/jacoco/index.html
```

JaCoCo enforces **70% line coverage** on the `service` package.
The build fails if coverage drops below this threshold.

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
│   │   └── enums/        # NotificationChannel, NotificationStatus, NotificationPriority
│   ├── repository/       # UserRepository, NotificationRepository, PreferenceRepository
│   ├── service/          # AuthService, NotificationService, PreferenceService,
│   │                     # DeduplicationService
│   └── util/             # JwtUtil, JwtAuthenticationFilter, RedisKeyUtil
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/     # V1__create_users.sql, V2__create_notifications.sql,
│                         # V3__create_preferences.sql
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
├── .github/workflows/    # ci.yml
├── docker-compose.yml
├── Dockerfile
├── .env.example
├── .gitignore
└── pom.xml
```

---

## Local Development (without Docker)

```bash
# Start only infrastructure services
docker-compose up mysql redis zookeeper kafka -d

# Run the application locally
mvn spring-boot:run

# Or with test profile
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

---

## Author

**Your Name**
- LinkedIn: [linkedin.com/in/your-profile](https://linkedin.com/in/your-profile)
- GitHub: [github.com/YOUR_USERNAME](https://github.com/YOUR_USERNAME)
- Email: your.email@example.com

---

## License

This project is licensed under the MIT License.
See [LICENSE](LICENSE) for details.