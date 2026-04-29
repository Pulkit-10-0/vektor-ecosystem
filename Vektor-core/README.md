# VectorGo Agent Engine

VectorGo is an emergency decision engine built as a Fastify backend. It receives emergency signals, normalizes them, classifies severity, selects a hospital, computes routing, stores the event, and emits realtime updates.

This repository implements the PRD as a working backend service with deterministic fallbacks so it can run locally without external API keys.

## What Goes In

### Emergency payload

`POST /api/emergency` expects a JSON body with:

- `source` - optional string, default `app`
- `timestamp` - optional ISO timestamp, defaults to `now`
- `idempotency_key` - optional string for retry-safe writes
- `location.lat` - required number
- `location.lng` - required number
- `patient.spo2` - required number
- `patient.heart_rate` - required number
- `patient.symptoms` - optional array of strings

Example:

```json
{
  "source": "iot",
  "timestamp": "2026-04-17T09:00:00.000Z",
  "location": { "lat": 12.97, "lng": 77.59 },
  "patient": {
    "spo2": 87,
    "heart_rate": 138,
    "symptoms": ["breathlessness"]
  }
}
```

### Offline sync payload

`POST /api/offline-sync` expects:

- `payload` - the same emergency payload accepted by `/api/emergency`
- `offline_decision` - decision data produced on-device or in cached mode

Example:

```json
{
  "payload": {
    "source": "iot",
    "timestamp": "2026-04-17T09:00:00.000Z",
    "location": { "lat": 12.97, "lng": 77.59 },
    "patient": { "spo2": 87, "heart_rate": 138 }
  },
  "offline_decision": {
    "decision": { "priority": "normal" },
    "hospital": { "hospital_id": "H003" }
  }
}
```

## What Comes Out

### GET /health

Returns a simple liveness response:

```json
{
  "status": "ok",
  "service": "vectorgo-agent-engine"
}
```

### GET /api/ping

Uptime ping endpoint for Render cron or any external monitor:

```json
{
  "status": "ok",
  "service": "vectorgo-agent-engine",
  "route": "ping"
}
```

### GET /ready

Readiness response includes database and integration status:

```json
{
  "status": "ready",
  "service": "vectorgo-agent-engine",
  "database": { "ok": true, "mode": "memory" },
  "integrations": {
    "gemini": false,
    "openrouter": false,
    "ors": false,
    "realtime": false
  }
}
```

### POST /api/emergency

Returns a full decision object with timing data:

```json
{
  "event_id": "uuid",
  "idempotency_key": "timestamp:lat:lng",
  "normalized": {
    "normalized": true,
    "confidence": 0.95,
    "validation": {
      "location_present": true,
      "timestamp_valid": true
    }
  },
  "classification": {
    "severity": "critical",
    "type": "respiratory",
    "confidence": 0.9,
    "source": "rules"
  },
  "decision": {
    "needs_icu": true,
    "priority": "high",
    "strategy": "best_equipped",
    "confidence": 0.86,
    "reason": "rule_engine: severity=critical, spo2=87, hr=138",
    "source": "rule_engine"
  },
  "hospital": {
    "hospital_id": "H001",
    "hospital_name": "Central General Hospital",
    "eta": 1,
    "distance_km": 0.53,
    "score": 0.962
  },
  "routing": {
    "source": "haversine",
    "distance_km": 0.53,
    "eta": 1,
    "geometry": null
  },
  "timing_ms": {
    "intake": 0.2,
    "classification": 0.1,
    "decision": 0.5,
    "selection_routing": 1.3,
    "total": 2.1
  },
  "constraints": {
    "total_latency_budget_ms": 3000,
    "under_budget": true
  }
}
```

### POST /api/offline-sync

Returns the online decision plus a comparison against the offline decision:

```json
{
  "synced": true,
  "online_decision": {
    "event_id": "uuid",
    "idempotency_key": "timestamp:lat:lng",
    "classification": {},
    "decision": {},
    "hospital": {},
    "routing": {},
    "timing_ms": {},
    "constraints": {}
  },
  "feedback": {
    "override_required": true,
    "reason": "cloud_decision_differs",
    "offline_summary": {
      "hospital_id": "H003",
      "priority": "normal"
    },
    "online_summary": {
      "hospital_id": "H001",
      "priority": "high"
    }
  }
}
```

## Runtime Rules

- Intake is pure JavaScript and never fails; it fills missing defaults and validates location/timestamp.
- Classification uses rule-based logic by default.
- Decision tries Gemini first, then OpenRouter if configured, then a rule engine fallback.
- Routing tries OpenRouteService if configured, then falls back to Haversine.
- The action step persists the event, emits realtime data, and uses an idempotency key for retry safety.
- Response payloads are trimmed if they exceed the configured maximum size.
- The backend is stateless; persistence is stored in PostgreSQL or the in-memory fallback.

## Agent Pipeline

1. Intake Agent
   - Input: raw emergency payload
   - Output: normalized payload, confidence, validation flags
2. Classification Agent
   - Input: normalized payload
   - Output: severity, type, confidence, source
3. Decision Agent
   - Input: normalized payload + classification
   - Output: ICU requirement, priority, strategy, confidence, reason, source
4. Hospital Selector
   - Input: hospital list + location + decision + classification
   - Output: selected hospital id, name, ETA, distance, score
5. Routing Agent
   - Input: origin + destination coordinates
   - Output: route source, distance, ETA, geometry
6. Action Agent
   - Input: pipeline result
   - Output: stored event id and idempotency key
7. Feedback Agent
   - Input: offline decision + online decision
   - Output: override comparison

## Error Responses

- `400 validation_error` - request body failed schema validation
- `413 payload_too_large` - response would exceed configured maximum size
- `500 internal_error` - unhandled server error

## Data Stored

- `emergency_events` - full event record and pipeline outputs
- `hospitals` - hospital metadata and capabilities
- `routing_cache` - cached route results for frequent trips

## Operational Realtime Store (Firebase)

This service now uses Firebase Realtime Database as the operational source of truth for live incident handling, while PostgreSQL remains the audit log.

- `/doctors/{doctorId}` - doctor availability (`currentstatus`, `currentIncidentId`)
- `/incidents/{incidentId}` - full incident payload for frontend screens
- `/channels/{channelName}` - latest channel event marker for listeners
- `/events/{eventId}` - replicated event envelope
- `/routing_cache/{cacheKey}` - route cache for repeated trips

Doctor assignment is lock-safe through Firebase transactions. A doctor is marked `busy` with `currentIncidentId` at assignment and marked `available` on discharge.

## Channel Events For Frontend

- `incident:{incidentId}` receives `incident_update`
- `doctor:{doctorId}` receives `patient_incoming` and `doctor_available`
- `patient:{patientUhid}` receives `sos_activated`

Pusher emits these events in realtime and Firebase mirrors the same channel state for read-based clients.

## Incident APIs For Frontend

- `GET /api/incidents/:incidentId` - fetch current incident payload
- `POST /api/incidents/:incidentId/discharge` - resolve incident and free assigned doctor

Discharge body example:

```json
{
  "currentstatus": "discharged",
  "released_by": "frontend",
  "notes": "Patient handed over"
}
```

## Environment Variables

### Required for production

- `DATABASE_URL` - Neon PostgreSQL connection string

### Optional AI and routing providers

- `GEMINI_API_KEY` - Gemini API access
- `GEMINI_MODEL` - Gemini model name, default `gemini-1.5-flash`
- `OPENROUTER_API_KEY` - fallback AI provider
- `OPENROUTER_MODEL` - OpenRouter model, default `openai/gpt-4o-mini`
- `ORS_API_KEY` - OpenRouteService API key

### Optional realtime

- `PUSHER_APP_ID`
- `PUSHER_KEY`
- `PUSHER_SECRET`
- `PUSHER_CLUSTER`

### Firebase operational store

- `FIREBASE_DATABASE_URL`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`

### Runtime

- `PORT` - server port, default `3000`
- `HOST` - bind host, default `0.0.0.0`
- `MAX_RESPONSE_BYTES` - response size cap, default `5120`
- `NODE_ENV` - environment name

## Quick Start

```bash
npm install
copy .env.example .env
npm run dev
```

Server starts on `http://localhost:3000`.

## Test

```bash
npm test
```

## Deployment Notes

- Render hosts the Node/Fastify backend.
- Neon provides PostgreSQL storage.
- Use Render cron pinging `/api/ping` every 10 minutes to reduce free-tier sleep.
- If Gemini, OpenRouter, or ORS are not configured, the system still works using rule-based and Haversine fallbacks.

## Current Scope

This repository covers the backend decision engine and its deterministic fallbacks. It does not include the actual mobile app, IoT firmware, or a device-hosted Gemma runtime.
