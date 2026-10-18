# Job Tracker

A local-first application for organizing job applications and keeping track of
recruitment conversations. Manage applications manually, or connect Gmail to
identify job-related emails and turn clear updates into application changes.
Uncertain email matches go to a review queue so they can be checked before
being applied.

## What it does

- Create, update, search, and track job applications and their status history.
- Optionally connect a Gmail account to sync relevant recruitment messages.
- Classify messages as updates to an existing application, a new application,
  or unrelated email.
- Use local Ollama AI as part of email classification, with review and decision
  rules to avoid automatically applying uncertain matches.
- Store application and email data in a local PostgreSQL database.

## Technology

- **Frontend:** Angular
- **Backend:** Java 21 and Spring Boot
- **Database:** PostgreSQL, with Flyway schema migrations
- **Email:** Gmail API with OAuth
- **AI classification:** Ollama
- **Local deployment:** Docker Compose

## Run locally with Docker

Configure the repository-root `.env` with the PostgreSQL settings and place
optional Gmail OAuth properties in `$HOME\.jobtracker\secrets.properties`.
Then, from the repository root, run:

```powershell
docker compose up --build -d
```

Open <http://localhost:4200>. Docker setup, configuration, and service
management instructions are in [DOCKER.md](./DOCKER.md).

## AI development assistance

GitHub Copilot and Claude were used as development helpers while building this
project. They assisted with coding and documentation; the project maintainer
directs and reviews the resulting changes.
