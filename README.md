# Ahlan VOC

Android app for offline Formbricks survey collection at stadiums in KSA. Surveyors load surveys from `https://ksa.formbricks.com` while online, run them fully offline, and the app pushes responses (and any captured files) back the moment a network is available.

## Capabilities

**Onboarding**
- Admin device fetches surveys, validates the API key, generates a setup QR (server URL + environment ID + read-only API key + project name).
- Surveyor devices scan the QR, enter their staff ID, and the app caches every survey in the configured environment to local storage.
- Image assets (welcome card, ending card, picture-selection thumbnails) are pre-warmed into Coil's 256 MB disk cache during refresh, so they render at the venue with no connectivity.

**Question types — v1 covers every type Formbricks v1 emits:**

| Type | UI | Stored value |
|---|---|---|
| `openText` | text/email/url/number/phone with optional char-limit counter | `string` |
| `multipleChoiceSingle` | radio list with "other" free-text support | `string` (label) |
| `multipleChoiceMulti` | checkbox list with "other" free-text support | `string[]` (labels) |
| `rating` | chip strip 1..N with low/high labels | `number` |
| `nps` | chip strip 0..10 with low/high labels | `number` |
| `cta` | primary + dismiss button | `"clicked"` / `""` |
| `consent` | single checkbox card | `"accepted"` / `""` |
| `pictureSelection` | 2-col image grid (Coil cached) | `string[]` (choice ids) |
| `date` | Material 3 date picker | ISO `YYYY-MM-DD` |
| `matrix` | rows × columns radio grid (horizontally scrollable) | `Map<rowLabel, colLabel>` |
| `ranking` | up/down reorder list | `string[]` (labels in chosen order) |
| `address` | 6 toggleable fields (line1, line2, city, state, zip, country) | positional `string[6]` |
| `contactInfo` | 5 toggleable fields (first, last, email, phone, company) | positional `string[5]` |
| `fileUpload` | system file picker, queued upload, multi-file support | `string[]` (URLs after upload) |
| `cal` | offline-friendly manual confirmation card | `"booked"` / `null` |

**Branching logic** — full Formbricks v1 logic engine in pure Kotlin (`domain/LogicEngine.kt`):
- Recursive condition trees (`{ connector: "and"|"or", conditions: [...] }`) with arbitrary nesting.
- Operators: `equals`, `doesNotEqual`, `contains`, `doesNotContain`, `startsWith`/`endsWith` and their negations, `isGreaterThan`/`isLessThan`/`Equal`, `equalsOneOf`/`isAnyOf`, `includesAllOf`/`includesOneOf`, `doesNotIncludeAllOf`/`OneOf`, `isBefore`/`isAfter`, plus unary `isSubmitted`/`isSkipped`/`isClicked`/`isNotClicked`/`isAccepted`/`isBooked`/`isPartiallySubmitted`/`isCompletelySubmitted`/`isSet`/`isNotSet`/`isEmpty`/`isNotEmpty`.
- Operands: static values, question/element answers, variables, hidden fields. Sub-field operands (`meta.row`, `meta.field`) work for matrix and address/contactInfo.
- Actions: `jumpToQuestion` (target may be a question id OR an ending id — same fallback semantics as the server), `calculate` (assign / concat / add / subtract / multiply / divide on text or number variables), `requireAnswer` (no-op at runtime — static `required` is still authoritative).
- `logicFallback` honoured when no rule matches.

**Endings** — both `endScreen` (thank-you card with optional image) and `redirectToUrl` (taps open in the device browser).

**Multi-language switching** — top-bar globe icon when `survey.showLanguageSwitch === true` and the survey has more than one enabled language. The chosen code rides in `response.language` and is used for every `localized()` lookup; `default` is the universal fallback.

**Variables and hidden fields** — collected in `LogicContext`, snapshotted into the queued response, and posted in `request.variables` / `request.hiddenFields`.

**Offline persistence and sync**
- Three Room tables: cached `surveys`, `queued_responses` (with `variablesJson` + `hiddenFieldsJson` snapshots), `queued_files` (file-upload queue).
- API key is stored in `EncryptedSharedPreferences` (Keystore-backed AES-GCM).
- Three WorkManager workers, all with `CONNECTED` constraint and exponential backoff:
  - `SurveyRefreshWorker` (every 6 h) refreshes survey definitions and pre-warms images.
  - `FileUploadWorker` (every 15 min) uploads queued files via the Formbricks public storage endpoint (handles S3 POST presign, S3 PUT presign, and self-hosted local PUT with signing headers).
  - `ResponseSyncWorker` (every 15 min) POSTs responses, but skips any whose bound files haven't finished uploading yet.
- Captured-then-orphaned dedup: each response carries a stable client UUID in `meta.source = "fbint:<uuid>"` so server-side duplicates created by a "succeeded but reported as failure" retry can be cleaned up.

**Sync status screen** — pending / synced / stuck counts plus a "sync now" button (kicks off both file-upload and response-sync one-shots).

## Build

You will need:

- Android Studio Ladybug or newer (AGP 8.7.x).
- JDK 17 (Studio bundles one; or `brew install openjdk@17`).
- Android SDK 35 platform + build-tools.

Open `/Users/essamharoon/FBINT` in Android Studio. The first sync will:
- Generate the Gradle wrapper (`gradlew` + jar) under `gradle/wrapper/` (the version is pinned in `gradle/wrapper/gradle-wrapper.properties`).
- Install AGP, Kotlin, Compose, Hilt, Room, Coil, ML Kit, CameraX, WorkManager from the configured repositories.
- Build & run on a connected device.

CLI build once Studio has installed the SDK:

```sh
cd /Users/essamharoon/FBINT
./gradlew :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r ...` or sideload to the surveyor devices.

## Operator runbook

### Admin (one device, online, one-time per project)

1. In Formbricks, **Settings → API keys** → create a key with **Read** permission on the project + environment that holds the surveys you want collected. Copy the key now — you cannot retrieve it later.
2. Find the **environment ID** in **Settings → Environments**.
3. Install Ahlan VOC on the admin device, open it, choose **Admin**.
4. Enter base URL (`https://ksa.formbricks.com`), the API key, the environment ID. Tap **Validate & continue** — the app calls `/api/v1/management/me` to confirm and pulls the project name.
5. The next screen shows the **setup QR**. Hand each surveyor's device to them and have them scan it.

### Surveyor (one-time per device)

1. Install Ahlan VOC, open it, choose **Surveyor**.
2. Grant camera permission, scan the admin's QR.
3. Enter your name or staff ID — this is attached to every response.
4. The app downloads the surveys list. From there, tap any survey to start collecting.

### During the event

- The runner works fully offline. Responses pile up in the local queue with a status badge on the survey list.
- File-upload questions copy each picked file into private app storage immediately; the queue sends them once a network is back.
- Whenever the device sees a network, WorkManager wakes up: files first, then responses (a response only POSTs once every file it depends on has uploaded).
- Tap the cloud icon to open **Sync status** for live counts and a manual **Sync now**.

### Recovering a stuck device

Open Sync status. If items show "Retry x3+" with the same error, the survey or file may have been deleted in Formbricks (the client returns 4xx and we mark it fatal). Confirm by opening that survey in Formbricks; if it's gone, the queued items cannot be recovered.

## Architecture

```
admin entry  ──▶  /me validate ──▶  QR (baseUrl, envId, apiKey, project)
                                             │
                                             ▼ scan
surveyor entry ──▶ name capture ──▶ Survey list (Room + Coil pre-warm)
                                             │
                                             ▼ tap
                              Survey runner (Compose + LogicEngine)
                                             │
                                             ▼ pick file        ▼ submit
                            QueuedFileEntity              QueuedResponseEntity
                                             │                   │
                                             ▼ network           │
                       FileUploadWorker ──▶ POST /storage         │
                       (S3 POST | S3 PUT | local PUT)             ▼ network
                                                       ResponseSyncWorker ──▶ POST /responses
                                                       (waits for bound files)
```

- `ConfigRepository` — `EncryptedSharedPreferences` (AES-GCM via Keystore) holds base URL, API key, environment ID, project name, surveyor ID.
- `SurveyRepository` — calls list+detail, caches full JSON in Room, pre-warms image cache via Coil.
- `LogicEngine` (pure Kotlin) — evaluates `question.logic[]` against the in-memory `LogicContext` (answers + variables + hidden fields) and returns the next step (question id, ending id, or done).
- `ResponseRepository` — captures response into the queue, binds referenced files, and (on sync) substitutes file URLs into `data` before POSTing.
- `FileQueueRepository` — copies picked URIs into private storage, requests signed URLs from Formbricks, uploads bytes via OkHttp, and writes back the canonical `fileUrl`.
- `SyncScheduler` — owns the periodic + one-shot WorkManager jobs.

## Security

- API key lives in `EncryptedSharedPreferences`; it's hard but not impossible to extract on a rooted device. Issue a **read-only** key — the QR cannot delete or modify surveys even if leaked.
- The QR encodes the API key in plaintext. Treat it like a password; don't photograph or share.
- Cleartext HTTP is disabled (`networkSecurityConfig`). Self-hosted servers must serve TLS.
- Backups (cloud + device transfer) are off so prefs and the local DB never leave the device.
- File uploads use the Formbricks **private** storage path (`/api/v1/client/{envId}/storage`) — uploaded files inherit Formbricks' configured access controls.

## Known limits

- POST `/responses` is not idempotent — a network blip after server-side success will create a duplicate. We mitigate by including the stable client UUID in `meta.source`; cleanup is manual on the server side.
- Cal.com bookings render an offline-friendly "mark as booked" card because the iframe can't load offline. If you need true Cal scheduling, the surveyor should hand the respondent a phone with connectivity for that step.
- The newer block/element survey shape isn't ingested yet — the v1 management API still emits the deprecated `questions[]` form, which is what we read.
- `requireAnswer` logic actions are no-ops; static `required` is always honoured. (The runtime mutation is rare in field-collection workflows and would complicate offline state.)
