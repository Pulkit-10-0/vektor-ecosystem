# Vektor Core Full Migration Handoff

Last updated: 2026-04-27
Owner intent: move to a new Copilot account and preserve complete context, decisions, fixes, and current known gaps.

This file is the single source of truth for the current state.

## 1) Repositories in Scope

1. vektor-core-agent (backend, Fastify + AI pipeline + Firebase + Postgres audit)
2. vektor-core-hms (frontend, Next.js dashboard)

Workspace paths:
- backend: E:/Code/Vektor-core-agent
- frontend: E:/Code/vektor-core-hms

## 2) High-Level Architecture

Flow:
trigger -> POST /api/emergency -> 7-stage pipeline -> hospital + doctor assignment -> incident persisted -> channel events -> frontend updates.

Core backend responsibilities:
- normalize emergency payload
- classify severity/type
- decide response strategy (AI/fallback)
- choose hospital (weighted scoring)
- compute route/ETA
- assign and lock doctor atomically
- publish incident and channel events

Data split:
- Postgres: audit logs + route cache (historical/support)
- Firebase Realtime DB: operational state (doctors/incidents/channels)

## 3) Emergency Pipeline (Verified)

File: src/pipeline/runEmergencyPipeline.js

Stage order:
1. intakeAgent
2. classificationAgent
3. decisionAgent
4. hospitalSelectorAgent
5. routingAgent
6. actionAgent
7. doctor lock path (inside operational store acquisition)

Response behavior:
- Full payload includes timing, routing, assigned doctor, hospital data.
- When response is too large, trimmed payload still includes:
  - hospital_id
  - hospital_name
  - eta
  - distance_km
  - score

## 4) Data Standardization Completed

### Hospitals
File: src/data/hospitals.js

IDs standardized to hsp_*:
- hsp_apollo_north
- hsp_city_care
- hsp_greenline
- hsp_skyline
- hsp_meridian

Coordinates are Delhi-based (28.6x, 77.2x), replacing earlier mismatched geography.

### Doctors
File: src/data/doctors.js

Current backend doctor count: 59 (verified by counting doc IDs in seed file).

Doctor IDs are doc-* slug format.
Current status fields used by backend logic:
- status
- currentstatus
- currentIncidentId

## 5) Doctor Locking and Release (Verified)

File: src/db/firebaseOperationalStore.js

Implemented and active:
- acquireAvailableDoctor() with 3-tier priority pools:
  1) same hospital + specialty
  2) same hospital + any specialty
  3) global specialty
- tryLockDoctor() uses Firebase transaction:
  - sets status/currentstatus -> busy
  - sets currentIncidentId -> incidentId
- releaseDoctor() uses transaction with incident ownership guard
- dischargeIncident() marks incident resolved and releases doctor

Important semantic note:
- current code uses busy/available.
- older notes mentioning on_case are outdated for this codebase.

## 6) Repository Seed and Refresh Behavior (Verified)

File: src/db/repository.js

Current behavior:
- initializeAuditSchemaAndSeed() always deletes and reseeds hospitals in audit DB
- init() initializes operational store using in-code hospitalSeed and doctorSeed
- stale DB rows are no longer trusted as source of operational truth

Effect:
- prevents old hospital ID drift and old cache reuse issues.

## 7) Backend Routes (Verified)

File: src/routes/emergency.js

Endpoints:
- POST /api/emergency
- POST /api/offline-sync
- GET /api/incidents/:incidentId
- POST /api/incidents/:incidentId/discharge

Discharge payload schema supports optional:
- currentstatus
- released_by
- notes

Script call requirement:
- send Content-Type: application/json
- send JSON body (use {} if empty)
Otherwise some callers return Unsupported Media Type.

## 8) Action Agent Current Truth (Important)

File: src/agents/actionAgent.js

Implemented:
- incident payload creation
- persistent write to incident store
- publish channel updates: incident-{id}, doctor-{id}, patient-{uhid}
- realtime emits to incident/doctor/admin-dashboard/patient channels

Not implemented in checked code:
- auto-discharge timer inside actionAgent

Why this matters:
- doctors stay busy until explicit discharge endpoint is called
- repeated simulations without discharge can exhaust pool and produce Doctor unavailable

Note on previous chat claims:
- there was a claim that auto-discharge was added
- verified current file does not include that logic
- treat auto-discharge as pending work

## 9) Frontend State (vektor-core-hms)

### A) Patient page runtime error fixed
File: src/app/(dashboard)/patients/page.tsx

Fixed issue:
- TypeError: (currentPatients || []).filter is not a function

Fix implemented:
- normalizePatientsResponse(payload) supports:
  - array payload
  - { patients: [] }
  - { data: [] }
- selectedPatients guarded with Array.isArray before filter
- safe optional checks in filter predicate

### B) Demo doctor parity mismatch still exists
File: src/lib/demo-doctors.ts

Observed:
- frontend demo doctors count: 33
- backend doctors count: 59

Impact:
- if frontend demo screens rely on demo-doctors.ts, names/count may not reflect backend assignments.

## 10) Major Problems Already Solved in This Migration

1. Hospital ID mismatch solved
   - old H001/H002/H003 replaced by hsp_* IDs

2. Geography mismatch solved
   - old non-Delhi coordinates replaced with Delhi coordinates

3. Seed staleness solved
   - hospital reseed now forced
   - firebase seed overwrite enabled on init

4. Trimmed response missing fields solved
   - hospital_name and distance_km preserved in compact response

5. Frontend patient registry filter crash solved
   - robust response normalization + array guarding

## 11) Current Known Runtime Risks

1. Gemini returns 404 in current environment frequently
   - fallback path handles most calls

2. Doctor saturation if discharge not called
   - expected until auto-discharge or strict discharge workflow is added

3. Simulator can fail intermittently
   - common causes: backend restart timing, network/web request hiccups, saturated doctor pool

## 12) Exact Commands (Copy/Paste)

From backend root:

Start backend:
- npm start

Simple emergency test (PowerShell body variable pattern):
- POST http://localhost:3002/api/emergency with location + patient vitals

Discharge an incident (PowerShell):
- Invoke-WebRequest -Uri "http://localhost:3002/api/incidents/$incidentId/discharge" -Method POST -ContentType "application/json" -Body '{}' -UseBasicParsing

Count backend doctor seed quickly:
- (Select-String -Path "E:/Code/Vektor-core-agent/src/data/doctors.js" -Pattern 'id: "doc-').Count

Count frontend demo doctor list:
- (Select-String -Path "E:/Code/vektor-core-hms/src/lib/demo-doctors.ts" -Pattern 'id: "doc-').Count

## 13) Priority TODO for Next Copilot Session

1. Implement auto-discharge in src/agents/actionAgent.js
   - timer target: 3 minutes
   - call repository.dischargeIncident safely
   - avoid duplicate release edge cases

2. Sync frontend demo-doctors with backend seed
   - update src/lib/demo-doctors.ts to 59 doctors
   - keep IDs exactly aligned

3. Add fast operational status endpoint
   - doctors by status/hospital for demo visibility

4. Harden simulator script
   - retries + graceful error handling

## 14) Minimal Onboarding Prompt for New Copilot Account

Use this prompt first in the new account:

"Read CURSOR_CONTEXT.md completely. Then verify current truth in these files before coding: src/agents/actionAgent.js, src/db/firebaseOperationalStore.js, src/db/repository.js, src/routes/emergency.js, src/data/doctors.js, and frontend src/app/(dashboard)/patients/page.tsx. Do not assume auto-discharge exists unless code confirms it."

## 15) Final Truth Snapshot

- backend pipeline: working
- hospitals: standardized and Delhi-aligned
- backend doctors: 59
- frontend demo doctors: 33 (mismatch)
- explicit discharge endpoint: working
- auto-discharge in actionAgent: NOT present yet
- patient page filter crash: fixed
