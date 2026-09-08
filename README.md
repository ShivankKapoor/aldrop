# Aldrop

Aldrop is a centralized authentication service. Applications ("platforms") register with the
service once, receive an API key, and from then on delegate user registration, login, two-factor
and session validation to Aldrop instead of implementing them again.

Sessions are opaque server-side rows, not JWTs: a token carries no claims, and a logged-out or
revoked session stops working the moment it is revoked rather than staying valid until it expires.

- Java 25 / Spring Boot 4, request handling on virtual threads
- PostgreSQL for platforms, users, sessions and TOTP challenges
- Argon2 password hashing, SHA-256 token hashes at rest
- No Spring Security: the two authentication schemes are plain servlet filters

## Two audiences, two credentials

| Caller | Endpoints | Credential |
|---|---|---|
| The Aldrop operator | `/platform/**` | HTTP Basic, from `PLATFORM_ADMIN_USERNAME` / `PLATFORM_ADMIN_PASSWORD` |
| A registered platform | `/auth/**` | `Authorization: Bearer <platform api key>` |
| Anyone | `/`, `/monitor` | none |

The API key on an `/auth` request decides which platform's user pool is used, so a username only
ever refers to a user within the calling platform. The same username can exist independently on
other platforms, and a session token issued to one platform is rejected for any other.

## Running it

Requires a PostgreSQL database with `schema.sql` applied, and a `.env` in the project root
(copy `.env.example` and fill it in).

```bash
psql -d aldrop -f schema.sql     # once
./run.sh                         # build the image and start it on :4000
./stop.sh                        # remove aldrop containers and images
```

`run.sh` builds with Podman and waits for `/monitor` to answer before reporting success. The `.env`
is baked into the image at build time, so the script rebuilds on every run rather than reusing a
stale image.

To run it directly instead:

```bash
./mvnw spring-boot:run
```

The schema is applied by hand; Hibernate does not manage DDL.

## API docs

With `ENV=QA`, Swagger UI is served at `/swagger-ui/index.html` and the spec at `/v3/api-docs`,
both behind the platform admin credentials. With `ENV=PROD`, or unset, they answer 404.

## Endpoints

### Platform management — `/platform`, Basic auth

| Method | Path | Purpose |
|---|---|---|
| POST | `/platform/create` | Register a platform. The response is the only time its API key is shown. |
| PATCH | `/platform/{id}/status` | Activate or deactivate. A deactivated platform's key stops being accepted; session rows survive, so reactivating restores any that have not expired. |
| PATCH | `/platform/{id}/rotate-key` | Issue a replacement API key. The old one dies immediately; user sessions are unaffected. |
| DELETE | `/platform/{id}` | Permanently delete the platform and, by cascade, all of its users, sessions and TOTP challenges. No undo. |

A platform is created with a session TTL (default 30 minutes), an optional cap on concurrent
sessions per user, and two flags: `totpAvailable` and `requireDeviceBinding`.

### Authentication — `/auth`, API key

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create a user on the calling platform. Usernames are lowercased and unique per platform. |
| POST | `/auth/login` | Username and password. Returns either a session token or a TOTP challenge — see below. |
| POST | `/auth/login/verify-totp` | Complete a two-factor login with an authenticator or backup code. |
| POST | `/auth/validate` | Check a session token; returns the user id and expiry. |
| POST | `/auth/logout` | End one session. |
| POST | `/auth/logout-all` | End every session for the token's user on this platform. |
| POST | `/auth/totp/enable` | Start two-factor enrolment for a logged-in user; returns a secret and `otpauth://` URI. |
| POST | `/auth/totp/confirm` | Prove a code works, switch two-factor on, and receive 8 single-use backup codes. |

### Service

`GET /` is a landing page; `GET /monitor` reports status, uptime and runtime details.

## Login

`POST /auth/login` returns one of two shapes and the caller has to check which:

```jsonc
{ "token": "…", "totpToken": null, "expiresAt": "…" }   // logged in
{ "token": null, "totpToken": "…", "expiresAt": "…" }   // two-factor required
```

When `totpToken` is set, the login is only half done. Send it back to `/auth/login/verify-totp`
with a current authenticator code or a backup code, within 5 minutes, to get a session token.

A wrong code counts as a failed attempt but leaves the challenge usable; the challenge is spent on
success, after 5 failed attempts, or after 5 minutes. An accepted code cannot be replayed, even
inside its own 30-second window.

## Two-factor enrolment

1. `POST /auth/totp/enable` with an active session token → secret and `otpauth://` URI for a QR code.
2. `POST /auth/totp/confirm` with a code generated from that secret → two-factor is on, and the
   response carries 8 single-use backup codes.

Show the backup codes once. They are the only way back in if the authenticator is lost, and there
is currently no way to regenerate them or turn two-factor off.

Enrolment requires both the platform flag `totpAvailable` and the user not already being enrolled.

## Sessions

`POST /auth/validate` is the endpoint a platform calls on each authenticated request. It returns
the user id and the session's expiry, and fails for an unknown, expired or revoked token, a token
belonging to another platform, a deactivated user, or a device-binding mismatch — all reported the
same way, as 401.

Tokens are 32 random bytes and are stored hashed, so the database never holds a usable token. When
a platform sets `maxSessionsPerUser`, the oldest sessions are evicted at login to stay within it.
Expired rows are swept hourly by a scheduled job.

## Device binding

A platform created with `requireDeviceBinding` must send `ipAddress` and `userAgent` on
`/auth/login`, `/auth/login/verify-totp` and `/auth/validate`; without them the call is rejected
with 400. A session then only validates from the same address and user agent it was issued to.
Platforms without the flag may still send both — they are recorded either way.

## Errors and limits

Errors are JSON. Validation failures return 400 with one entry per rejected field; everything else
returns `{"error": "…"}` with the relevant status.

| Status | Means |
|---|---|
| 400 | Validation failed, or device binding is required and the fields were missing |
| 401 | Bad platform credentials, wrong username/password, or an invalid session or TOTP token |
| 404 | No such platform |
| 409 | Name or username already taken, two-factor unavailable or already enabled |
| 429 | Rate limited |

Login is limited to 5 failed attempts per username per 15 minutes, cleared by a success. TOTP
verification, enrolment and confirmation are each limited to 5 attempts per user per 15 minutes.

Wrong username and wrong password are deliberately not distinguished, and neither is a deactivated
account. Every request field has a maximum length (see `Validation/FieldLimits`), so an oversized
password never reaches Argon2 and an oversized username never becomes a rate-limiter cache key.

## Tests

```bash
./mvnw test -Dtest='!AldropApplicationTests,!*KarateRunner'   # unit tests, no database needed
./run-karate-tests.sh                                          # Karate end-to-end flows
```

The unit tests are what CI runs on every push and pull request. The Karate features cover the auth,
TOTP and device-binding flows end to end and need a running service and database, with `.env`
present; the runner script sources it for you.
