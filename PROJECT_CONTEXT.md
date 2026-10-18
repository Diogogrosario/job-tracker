# PROJECT_CONTEXT.md - Local AI Job Application Tracker

This file is the single source of truth for the project. Read it fully before any task.
The project setup is finished and verified. Backend and frontend both run. From now on, the work is writing feature code. I will give you one coding task at a time. Do only that task.

---

## 1. Working Rules (READ FIRST)

1. Do only the task I give you. Do not start other work or "improve" unrelated files.
2. Small, complete changes. Create or edit only the files the task needs. One layer or feature at a time.
3. Never invent files, dependencies, versions, or results. If something is missing or unclear, say so and ask. Never claim a file exists or a command succeeded unless you saw it in a tool result.
4. Look before you write. Before creating or editing code, list the relevant folder and read the existing files you depend on.
5. Ask before running commands that install, delete, or overwrite things. Never start long-running processes such as `spring-boot:run`, `npm start`, or `docker compose logs -f` in the agent environment. Short compile/test commands are fine.
6. Never read, print, or edit `.env` (secrets). You may edit `.env.example`.
7. Flyway migrations are immutable. Never edit an already-applied migration. To change the schema, add a new migration file instead.
8. After each task, tell me: (a) the files created or changed, (b) the command to verify, and (c) the expected result. Then stop and wait.
9. If something fails, show me the exact error and propose one fix. Do not retry blindly more than twice.
10. Never hardcode secrets or URLs. Use configuration and environment variables.
11. Keep it simple. No extra libraries or unnecessary abstractions. Do not add a dependency without asking.
12. Shell is PowerShell on Windows: use `;` instead of `&&`, `\.\mvnw.cmd` instead of `./mvnw`, and `curl.exe` instead of `curl`.

---

## 2. Project Goal

A local-first job application tracker that:

- Lets me manually add and manage job applications.
- Connects to Gmail with OAuth.
- Reads new recruitment emails from Gmail.
- Uses a classifier to determine whether an email is:
  - an update to an existing application,
  - a new application to create,
  - or not relevant.
- Creates a human review item when the classification is uncertain.
- Updates application status and metadata only after approval or when the rule is clear.
- Keeps the data local and only relies on Gmail for inbound communication.

---

## 3. Environment and Stack

- OS: Windows, PowerShell, VS Code.
- Java: JDK 21 required by the project.
- Node/npm for the Angular frontend.
- PostgreSQL in Docker for local persistence.
- Spring Boot 4 + Java 21 backend.
- Angular frontend.
- Flyway migrations for schema changes.
- Gmail API + OAuth integration.
- Local AI via Ollama for email classification.

| Component | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 4 |
| Frontend | Angular |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Migrations | Flyway |
| Local AI | Ollama HTTP API |
| Email Provider | Gmail API with OAuth |
| API | REST + JSON |

---

## 4. Current State (verified working)

```
job-tracker/
├── backend/                 # Spring Boot app
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── src/main/
│       ├── java/com/jobtracker/
│       └── resources/
│           ├── application.yml
│           └── db/migration/
├── frontend/                # Angular app
│   ├── package.json
│   ├── angular.json
│   └── src/
├── docker-compose.yml       # PostgreSQL only
├── .env.example
├── .env                     # Not in Git. Never read it.
├── PROJECT_CONTEXT.md
├── STARTUP.md
```

Verified:
- backend and frontend both run locally,
- PostgreSQL is healthy,
- the backend can reach the database,
- health checks are available via `/api/actuator/health`.

---

## 5. Architecture

The app is split into clear layers:

- `backend/src/main/java/com/jobtracker/application`:
  - job application domain and service layer
  - CRUD and status update flows
- `backend/src/main/java/com/jobtracker/email`:
  - Gmail sync and mailbox connection logic
  - email processing and classification
  - review queue for uncertain matches
- `backend/src/main/java/com/jobtracker/common`:
  - common errors and API response behavior
- `backend/src/main/java/com/jobtracker/config`:
  - app configuration and external client setup
- `frontend/src/app`:
  - pages for application list/detail/form
  - services for API access, Gmail sync, and review queue
  - models for application, email, and status data

This is the intended architecture for the current use case:
1. Add applications manually in the web portal.
2. Connect Gmail and fetch recruiter emails.
3. Classify each message.
4. Update an application or create a new one from a reviewed email.
5. Keep the review queue for ambiguous cases.

---

## 6. Main Workflow

1. The user adds a job application manually through the web UI.
2. The user connects a Gmail account via OAuth.
3. Gmail sync fetches recent messages.
4. The classifier decides if the email is relevant and whether it belongs to an existing application or suggests a new one.
5. If the result is uncertain, the item is queued for human review instead of directly mutating an application.
6. After approval, the application status is updated or a new application is created.
7. The final state is stored in the local database.

This workflow is the scope of the project. Do not add unrelated features or large generic tools.

---

## 7. Database and Data Model

The database is PostgreSQL. Schema changes must be managed through Flyway migrations.

Core tables include:
- `job_application`
- `application_status_history`
- any supporting email / mailbox tables used by Gmail sync and processing

Rules:
- never edit an old migration,
- add a new migration for every schema change,
- keep the Java entities aligned with the database schema,
- status values should stay consistent with the application domain.

---

## 8. Security and Privacy

- Application data stays in the local database.
- Never store email passwords in plain text.
- Use OAuth with minimal permissions for Gmail access.
- Store refresh tokens securely and encrypted when needed.
- Keep `.env` and secrets out of Git.
- Never log full email bodies or tokens.
- Keep the app local; do not expose local services publicly.

---

## 9. How to Run and Verify

I run these in my own terminals. After you change code, tell me which ones to run.

```powershell
# database
cd job-tracker; docker compose up -d db

# backend
cd job-tracker/backend; .\mvnw.cmd spring-boot:run

# frontend
cd job-tracker/frontend; npm start
```

Health checks:

```powershell
curl.exe http://localhost:8080/api/actuator/health
curl.exe http://localhost:4200/api/actuator/health
```

Compile check:

```powershell
cd job-tracker/backend; .\mvnw.cmd clean compile
```

---

## 10. Definition of Done

A change is done when:

- it matches the current job-tracker workflow,
- the backend and/or frontend still compile and run,
- the change is small and focused,
- unrelated code is left alone,
- no secrets or generated artifacts are committed.

---

## 11. Common Problems and Fixes

- `JAVA_HOME` not set correctly: fix the JDK path.
- Postgres auth fails: fix `.env` or reset the Docker volume.
- Database connection refused: start Docker PostgreSQL.
- Schema validation errors: compare the entity with the Flyway schema; add a migration rather than editing old ones.
- API health endpoint missing: verify `application.yml` and actuator config.
- Frontend proxy errors: make sure the Angular app runs with the proxy config and the backend is on.

This project has one clear purpose: manually track applications and automatically classify Gmail recruitment emails, with review approval for ambiguous cases.
