# Payment Status Inbox

A local demo where an operator creates a payment and follows its status. A fake
provider implemented in the backend accepts the payment immediately and reports
its outcome later through an HTTP callback to the same backend.

## Requirements

- Java JDK 21, with `JAVA_HOME` pointing to the installation.
- Node.js 22.18+ within the 22.x release line, or Node.js 24.12+.
- npm, included with Node.js.

The backend uses Spring Boot 3.5.6, Gradle (wrapper committed), and file-based H2.
The frontend uses Vue 3 and TypeScript, with a committed npm lockfile. No external
payment service, credentials, or separate database server is needed.

## Start locally

The following PowerShell commands start from the repository root. Keep the two
terminals open.

**Terminal 1 - backend:**

```powershell
cd backend
.\gradlew.bat bootRun
```

The backend listens at `http://localhost:8080`. Its database is stored in
`backend\data\payments.mv.db` when started from the `backend` directory. Payments
survive backend restarts.

**Terminal 2 - frontend:**

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:3000`. Enter an amount, select a currency, and enter a
customer reference. The page shows submission progress and polls for the payment
status approximately every two seconds while it is PENDING or UNKNOWN. Polling
stops on SUCCEEDED or FAILED and is cancelled when a new submission starts.

The Vite development server proxies `/api` requests to the local backend. Start
both processes to use the page.

### Offline operation

At runtime, the application only communicates over localhost and uses the local H2
database. Initial setup requires network access to download the Gradle
distribution, Java dependencies, and npm packages unless they are already cached.

After dependencies have been downloaded and `npm ci` has completed, start the
backend without dependency downloads using:

```powershell
cd backend
.\gradlew.bat --offline bootRun
```

The frontend can then run with `npm run dev` from `frontend` without network access.
The source checkout alone does not include downloaded dependencies.

## Tests and build

Run backend tests from the repository root:

```powershell
cd backend
.\gradlew.bat test
```

The HTML test report is written to `backend\build\reports\tests\test\index.html`.
Add `--offline` to the Gradle command when dependencies are already cached.


## Choose the fake provider outcome

Edit `backend\src\main\resources\fake-provider-scenario.json` and restart the
backend. The scenario is loaded once at startup and runs separately for each new
payment.

For a single successful outcome, use:

```json
{
  "callbacks": [
    { "status": "CAPTURED" }
  ]
}
```

Choose another outcome by changing `status`:

| Provider status | Application status |
|---|---|
| `AUTHORIZED` | `PENDING` |
| `CAPTURED` | `SUCCEEDED` |
| `DECLINED` | `FAILED` |
| `ERROR`, an unknown string, or `null` | `UNKNOWN` |

Omitting `status` also produces UNKNOWN. The first callback is scheduled after a
random 2-5 second delay following payment creation. Each subsequent callback waits
another 2-5 seconds after the preceding delivery attempt finishes.

The checked-in scenario sends ERROR followed by CAPTURED:

```json
{
  "callbacks": [
    { "status": "ERROR" },
    { "status": "CAPTURED" }
  ]
}
```

To exercise callback ordering entries may also specify ISO-8601 `occurredAt`:

```json
{
  "callbacks": [
    {
      "status": "AUTHORIZED",
      "occurredAt": "2026-10-28T10:00:00Z"
    },
    {
      "status": "AUTHORIZED",
      "occurredAt": "2026-10-28T10:00:00Z"
    }
  ]
}
```

Absent event IDs and timestamps are generated at delivery time.
An optional `providerPaymentId` overrides the callback target for that entry, for example
`"prov-unknown"` to exercise a 404 response.

To load a separate local scenario file instead (path relative to `backend`):

```powershell
cd backend
.\gradlew.bat bootRun --args="--fake-provider.scenario=file:./scenarios/declined.json"
```

Create that file before starting. Invalid scenarios fail startup. Callback
delivery failures are logged but not retried.

## API

The backend endpoints have no `/api` prefix; that prefix is only used by the
frontend development proxy.

| Endpoint | Behavior |
|---|---|
| `POST /payments` | Creates a PENDING payment; returns 201 with `providerPaymentId`. Requires an `Idempotency-Key` UUID header. Invalid input returns 400. |
| `GET /payments/{id}` | Looks up by **provider payment ID**. Returns payment details, including a separate internal `id`; returns 404 if not found. |
| `POST /provider-callbacks` | Returns 200 for processed or ignored callbacks, 404 for an unknown provider payment ID, and 400 for invalid input. |

Payment amounts are decimal strings, converted to `BigDecimal` and stored as
`DECIMAL(19,2)`. They must be positive with at most 17 integer and 2 fractional
digits. Supported currencies are EUR, USD, and CZK. Customer reference length is
1-30 characters.

Creation retries with the same key and matching data return the original provider
payment ID. Reusing the key with different data returns 409. Keys are persisted
without automatic expiry.