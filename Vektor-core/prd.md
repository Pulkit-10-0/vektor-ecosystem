Good—now we lock this into a **real PRD**. No fluff, just something you can actually build from.

I’m going to define:

* system scope
* agents (with requirements)
* APIs/tools (FREE-first choices)
* constraints + limits
* infra decisions

---

# 📄 **VECTORGO AGENT ENGINE — PRD (v1)**

---

## 1. 🧠 Product Definition

**System Name:** VectorGo
**Module:** Emergency Decision Engine (Agent Layer)

### Purpose

A real-time decision system that:

* processes emergency signals (IoT/app)
* classifies severity
* selects optimal hospital
* orchestrates response
* syncs offline → online decisions

---

## 2. 🎯 Core Objectives (MVP)

* < 3s total decision latency
* works offline (Gemma) + cloud override
* deterministic + explainable output
* zero critical dependency on a single AI API

---

# 3. 🧩 SYSTEM COMPONENTS

---

## 🔵 A. Orchestrator (Render Backend)

### Stack

* Node.js + Fastify (recommended over Express)
* Hosted on **Render (free tier)**

### Responsibilities

* receives `/api/emergency`
* runs agent pipeline
* manages retries + fallbacks
* syncs state to DB + frontend

### Requirement

* uptime workaround: cron ping (every 10 min)
* stateless API + DB-backed state

---

## 🟢 B. Database

### Recommended

* **Neon PostgreSQL (free)**

### Stores

* emergency_events
* hospital metadata
* cached routing decisions

---

## 🟡 C. AI Layer

### Primary

* **Gemini API (Google AI Studio)**

### Secondary (fallback)

* Grok / OpenRouter (optional)

### Usage Constraints

* ONLY for:

  * classification (optional)
  * decision reasoning

---

## 🔴 D. Edge AI (Offline)

* Gemma (on-device)
* fallback decision engine

---

# 4. 🤖 AGENT PIPELINE (FINALIZED)

---

# 4.1 📥 Intake Agent

### Purpose

Normalize input

### Requirements

* Pure JS logic (NO AI)
* Validate:

  * location present
  * timestamp valid
* Fill defaults

### Output Schema

```json
{
  "normalized": true,
  "confidence": 0.9
}
```

### Constraints

* < 50ms
* must NEVER fail

---

# 4.2 🚨 Classification Agent

### Purpose

Determine severity + type

### Implementation Options

* Rule-based (MVP preferred)
* OR Gemini (light call)

### Example Rules

* SpO2 < 90 → critical
* HR > 130 → high risk

### Output

```json
{
  "severity": "critical",
  "type": "respiratory",
  "confidence": 0.85
}
```

### Constraints

* < 300ms
* fallback → rules

---

# 4.3 🧠 Decision Agent (CORE)

### Model

* Gemini (primary)

### Purpose

Decide:

* requirements
* routing strategy

### Output

```json
{
  "needs_icu": true,
  "priority": "high",
  "strategy": "best_equipped",
  "confidence": 0.9,
  "reason": "low SpO2 + high HR"
}
```

### Requirements

* strict JSON output
* no free text blobs

### Constraints

* timeout: 1.5s
* max tokens: ~300
* fallback → rule engine

---

# 4.4 🏥 Hospital Selector

### Purpose

Pick best hospital

### Implementation

* PURE backend logic

---

## 📡 Hospital Data Requirements

Each hospital must have:

```json
{
  "id": "...",
  "lat": ...,
  "lng": ...,
  "has_icu": true,
  "specialties": ["cardiac"],
  "capacity_score": 0.7
}
```

---

## 📍 Distance Calculation

### Option 1 (recommended)

* Haversine formula (free, local)

### Option 2

* OSRM (Open Source Routing Machine)

---

## 🗺️ Map / Routing API (FREE OPTIONS)

### 🥇 Best Choice: **OpenRouteService**

* free tier available
* good for routing + ETA

### 🥈 Alternative: **OSRM (self-host or public)**

* completely free
* no API key needed

### 🥉 Fallback: **Google Maps**

* NOT free long-term

👉 Recommendation:

> Use **OpenRouteService + Haversine fallback**

---

## Output

```json
{
  "hospital_id": "H123",
  "eta": 10,
  "score": 0.82
}
```

---

# 4.5 🚑 Routing Agent

### API

* OpenRouteService

### Requirements

* route geometry
* ETA

### Constraints

* cache frequent routes

---

# 4.6 ⚡ Action Agent

### Purpose

Execute system actions

### Responsibilities

* create DB record
* trigger frontend alert
* notify ambulance

### Requirements

* idempotency key
* retry-safe

---

# 4.7 🔁 Feedback Agent

### Purpose

Sync offline ↔ online

### Responsibilities

* compare Gemma vs cloud decision
* override if needed
* push update to app

---

# 5. 🔄 OFFLINE → ONLINE FLOW

```id="7h91qj"
1. IoT (Gemma) creates local decision
2. Event cached locally
3. Network restored
4. Sent to Render
5. Full pipeline runs
6. Decision override sent back
```

---

# 6. 🔒 SYSTEM CONSTRAINTS

---

## ⏱ Latency Budget

| Step                | Limit    |
| ------------------- | -------- |
| Intake              | 50ms     |
| Classification      | 300ms    |
| Decision            | 1.5s     |
| Selection + Routing | 500ms    |
| **Total**           | **< 3s** |

---

## 🔁 Retry Strategy

* max retries: 2
* exponential backoff

---

## 🧠 Fallback Hierarchy

1. Gemini
2. Secondary model
3. Rule-based logic

---

## 📦 Response Limits

* max payload: 5KB
* must be JSON

---

# 7. 🧱 REQUIRED SERVICES (FINAL LIST)

---

## Backend

* Render (Node.js Fastify)

## Database

* Neon PostgreSQL

## AI

* Gemini API (primary)
* optional fallback: OpenRouter

## Maps

* OpenRouteService
* fallback: Haversine

## Realtime

* Pusher (free tier)

---

# 8. 🚨 RISKS (REALISTIC)

---

## ❌ HF endpoint as core brain

→ unreliable for real-time

## ❌ Overusing AI

→ latency + cost spike

## ❌ No fallback logic

→ system failure


