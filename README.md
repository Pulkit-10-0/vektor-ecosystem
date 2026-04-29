# Vektor Ecosystem

Vektor is an emergency response ecosystem made up of two cooperating codebases:

- `Vektor-app`, the native Android client that handles local profile storage, sensor-driven emergency detection, on-device AI inference, and the user experience.
- `Vektor-core`, the VectorGo agent engine that receives emergency payloads, normalizes and classifies them, selects hospitals, computes routing, persists events, and publishes realtime updates.

The repository also includes a prebuilt Android artifact, `app-release.apk`, so the mobile client can be installed and tested without rebuilding immediately.

## System Overview

The design is intentionally split between a local-first mobile layer and a deterministic backend orchestration layer.

1. The Android app keeps private data on device, monitors sensors, and runs local AI with Cactus and Gemma for offline emergency analysis.
2. When an incident must be escalated, the app sends a single emergency payload to the VectorGo endpoint.
3. The backend validates the request, runs the decision pipeline, picks a hospital, calculates routing, and stores the result.
4. Realtime incident state is mirrored for operational screens and follow-up workflows.

The current documentation in this workspace describes two important persistence paths on the backend: PostgreSQL for audit storage and Firebase Realtime Database for operational realtime state.

## Repository Layout

| Path | Purpose |
|---|---|
| `Vektor-app/` | Android application source, product requirements, and embedded Cactus SDK materials |
| `Vektor-app/prd.md` | Product requirements for the mobile app |
| `Vektor-app/cactus.md` | High-level Cactus integration reference |
| `Vektor-app/cactus/` | Cactus SDK sources, docs, and platform wrappers |
| `Vektor-core/` | VectorGo backend and operational docs |
| `Vektor-core/prd.md` | Product requirements for the backend decision engine |
| `Vektor-core/README.md` | Backend usage, API surface, and runtime notes |
| `app-release.apk` | Prebuilt Android APK artifact kept at the repository root |

## Vektor-App

`Vektor-app` is a native Android project written in Kotlin. The product document defines it as an emergency identity card, health companion, and silent guardian that is meant to keep working even when the device is offline.

### Core responsibilities

- Store profile data locally using encrypted on-device storage.
- Monitor motion, location, and sensor conditions for emergency detection.
- Run on-device inference through Cactus SDK and Gemma.
- Build and queue emergency payloads for retry-safe delivery.
- Present the user experience in Jetpack Compose.

### Architecture notes from the PRD

The mobile PRD describes the app as a layered system:

- Data layer for encrypted preferences, Room, and internal file storage.
- Sensor layer for accelerometer, gyroscope, barometer, and location monitoring.
- AI layer backed by Cactus SDK.
- Emergency layer for countdown, payload creation, and retry logic.
- Communication layer for the VectorGo client and SMS fallback.
- UI layer built with Jetpack Compose.

### Cactus integration

The app depends on Cactus for local AI. The Cactus documentation in `Vektor-app/cactus/` explains the engine, graph API, platform wrappers, and SDK-specific quickstarts. Useful entry points include:

- `Vektor-app/cactus/README.md`
- `Vektor-app/cactus/docs/quickstart.md`
- `Vektor-app/cactus/docs/cactus_engine.md`
- `Vektor-app/cactus/docs/cactus_graph.md`
- `Vektor-app/cactus/android/README.md`
- `Vektor-app/cactus/flutter/README.md`
- `Vektor-app/cactus/python/README.md`
- `Vektor-app/cactus/rust/README.md`

The app-level overview in `Vektor-app/cactus.md` and `Vektor-app/prd.md` makes the intended contract clear: Cactus handles the offline brain, while VectorGo is the external orchestration surface.

## Vektor-Core

`Vektor-core` is the VectorGo agent engine. The backend PRD frames it as a real-time decision system that processes emergency signals, classifies severity, selects hospitals, orchestrates response, and syncs offline decisions when connectivity returns.

### Core responsibilities

- Accept emergency payloads from the app or other sources.
- Normalize and validate incoming data.
- Classify severity using deterministic logic with cloud fallbacks where configured.
- Select the best hospital and route using live or fallback data.
- Persist emergency events and emit realtime updates.
- Compare offline and online decisions during sync.

### Documented endpoints

The backend README describes the primary API surface:

- `GET /health` for liveness.
- `GET /api/ping` for uptime monitoring.
- `GET /ready` for readiness checks.
- `POST /api/emergency` for the main emergency intake path.
- `POST /api/offline-sync` for reconciling an offline decision with the online result.
- `GET /api/incidents/:incidentId` and `POST /api/incidents/:incidentId/discharge` for incident workflows.

### Runtime behavior

The backend documentation emphasizes a few important constraints:

- Intake should be deterministic and never fail on routine validation paths.
- Classification and routing should fall back to rules when external services are not configured.
- Idempotency is used to make retry-safe writes practical.
- The total response budget is designed to stay under the emergency latency target.

## Getting Started

The workspace is split into two independently runnable projects. The exact commands may vary by local toolchain, but the documented defaults are below.

### Backend

```bash
cd Vektor-core
npm install
copy .env.example .env
npm run dev
```

Run tests with:

```bash
cd Vektor-core
npm test
```

### Android app

Open `Vektor-app` in Android Studio, or use the Gradle wrapper from that project root.

Typical Android build flow:

```bash
cd Vektor-app
./gradlew assembleDebug
```

The repository root also contains `app-release.apk` for immediate installation and smoke testing.

## Build and Packaging Notes

The Android tree includes the main app project plus a Cactus SDK subtree. If you are rebuilding the SDK bindings or native components, the supporting scripts and build files live under `Vektor-app/cactus/` and `Vektor-app/`.

Important files to know about:

- `Vektor-app/build.gradle.kts`
- `Vektor-app/app/build.gradle.kts`
- `Vektor-app/build_cactus_android.sh`
- `Vektor-app/cactus/android/build.sh`
- `Vektor-app/cactus/android/CMakeLists.txt`
- `Vektor-core/package.json`
- `Vektor-core/src/`

## Documentation Map

If you want to understand the system before changing code, start with these docs in order:

1. `Vektor-app/prd.md` for the mobile product definition and architecture.
2. `Vektor-core/prd.md` for the backend agent engine requirements.
3. `Vektor-core/README.md` for the current backend API and runtime behavior.
4. `Vektor-app/cactus/README.md` for the Cactus SDK overview and benchmarks.
5. `Vektor-app/cactus/docs/quickstart.md` for platform-specific SDK entry points.
6. `Vektor-app/cactus/docs/cactus_engine.md` and `Vektor-app/cactus/docs/cactus_graph.md` for the native engine APIs.

## Notes For Contributors

- Keep mobile-first behavior in `Vektor-app` aligned with the PRD, especially the local-first and emergency-only networking expectations.
- Keep the backend deterministic where possible, especially around intake, validation, and routing fallbacks.
- Do not remove `app-release.apk` unless the distribution strategy changes.
- Avoid adding new external dependencies without documenting why they are needed and where they fit in the architecture.
