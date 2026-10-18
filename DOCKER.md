# Run the whole app with Docker

This starts PostgreSQL, the Spring Boot API, and the Angular frontend together.
You need Docker Desktop installed and running.

## Check the configuration files

Run these commands in PowerShell from the repository root:

```powershell
Test-Path .env
Test-Path "$HOME\.jobtracker\secrets.properties"
```

Both commands should return `True`. Compose reads `POSTGRES_DB`,
`POSTGRES_USER`, and `POSTGRES_PASSWORD` from the repository-root `.env` file.
The `secrets.properties` file at `$HOME\.jobtracker\secrets.properties` is
mounted read-only into the backend container as a Docker secret; it is not
copied into the image.

Keep the same `POSTGRES_PASSWORD` for an existing database volume. Changing the
value in `.env` does not change the password of a database that has already
been initialized.

## Build and start

From the repository root, run:

```powershell
docker compose up --build -d
```

Docker builds the frontend and backend images, starts the database, waits for it
to become healthy, and then starts the API and web server. On the first run,
image downloads and builds may take a few minutes.

Check that all three services are running:

```powershell
docker compose ps
```

Open the app at <http://localhost:4200>. The frontend and API use that same
address. PostgreSQL is also available only on this PC at `localhost:5432`.
Database data is retained in the `pgdata` Docker volume across restarts.

## Logs and stop

To inspect startup output:

```powershell
docker compose logs -f
```

Press `Ctrl+C` to stop following the logs; this does not stop the app. To stop
the containers while keeping the database:

```powershell
docker compose down
```

Start them again later with `docker compose up -d`. To also permanently delete
the saved database, use `docker compose down -v`.

## If the API reports a database password error

Compose uses `.env` to configure the database, but values from `.env` are not
automatically passed to every container. The Compose configuration now
explicitly passes the same `POSTGRES_PASSWORD` to the backend. Apply the fix
and recreate the services from the repository root:

```powershell
docker compose up --build -d
```

If authentication still fails, the existing `pgdata` volume may have been
initialized with a different password. Update `POSTGRES_PASSWORD` in `.env` to
the password already used by that database. Do not run `docker compose down -v`
unless you intend to permanently delete all saved database data.

## Optional Gmail integration

Register this exact URI as an Authorized redirect URI in the Google OAuth
client settings:

```text
http://localhost:8080/api/mailbox/oauth/callback
```

The API publishes port 8080 on localhost for Google's callback; the app itself
and its API requests are still served through <http://localhost:4200>. In
`$HOME\.jobtracker\secrets.properties`, use these exact
property names (the application reads these uppercase keys):

```properties
GOOGLE_CLIENT_ID=your-google-oauth-client-id
GOOGLE_CLIENT_SECRET=your-google-oauth-client-secret
TOKEN_ENCRYPTION_KEY=your-base64-encoded-32-byte-key
```

The OAuth error means the running backend received an empty client ID or client
secret. The mounted file is available to the container at
`/run/secrets/backend_properties`. Because Docker mounts it without a file
extension, the app explicitly tells Spring to load it as a `.properties` file.
Use the exact uppercase names above rather than `app.google.client-id` /
`app.google.client-secret`. After changing the file, restart the backend so
Spring reloads it:

```powershell
docker compose restart backend
```

Keep `.env` and `secrets.properties` private. Neither should be committed to
source control or copied into a Docker image.
