# RCR Frontend Integration Context

## 🚀 What we are building
We are connecting the Vektor Core HMS frontend (Next.js) to the Vektor-core-agent backend in real time.

The backend runs an AI agent pipeline that processes emergency cases and assigns hospitals + doctors.

The frontend must:
- Listen to real-time updates
- Fetch incident data
- Render 3 different user views:
  - Doctor briefing screen
  - Patient portal screen
  - Responder flash screen

---

## ⚙️ Current Backend State

### ✅ Already implemented
- POST /api/emergency receives emergency payload
- AI agent pipeline runs (~2.5s)
- Best hospital is selected
- Events stored in PostgreSQL
- Basic broadcasting exists (Pusher)

---

### ❗ What needs to be added (IMPORTANT)

1. Replace operational DB with Firebase Realtime DB  
   - PostgreSQL stays ONLY for audit logs  
   - Firebase is the source of truth for real-time operations  

2. Doctor availability logic
   - A doctor MUST NOT handle 2 patients at once  

3. Channel-based broadcasting
   - Each user listens to their own channel  

4. Discharge API
   - Frontend tells backend when patient is discharged  
   - Backend frees doctor  

---

## 🔥 Firebase Database Schema

### /doctors/{doctorId}
```json
{
  "id": "DOC-089",
  "name": "Dr. Sharma",
  "specialty": "Cardiologist",
  "hospitalId": "HOSP-007",
  "status": "available",
  "currentIncidentId": null,
  "lastUpdated": "timestamp"
}
/incidents/{incidentId}
{
  "incidentId": "INC-2026-0423-204",
  "status": "active",
  "createdAt": "timestamp",

  "location": {
    "venue": "Hotel XYZ",
    "room": "204",
    "floor": "2",
    "gps": { "lat": 28.6139, "lng": 77.209 }
  },

  "crisis": {
    "type": "medical",
    "subtype": "cardiac",
    "severity": "critical",
    "verified": true
  },

  "patient": {
    "id": "PAT-00234",
    "name": "Ravi Sharma",
    "age": 54,
    "blood_type": "B+",
    "allergies": ["Penicillin"],
    "conditions": ["Diabetes"],
    "critical_flags": ["Arrhythmia"]
  },

  "assignedHospital": {
    "id": "HOSP-007",
    "name": "City Hospital",
    "distance": "2.3km",
    "etaAmbulance": "8 mins"
  },

  "assignedDoctor": {
    "id": "DOC-089",
    "name": "Dr. Sharma"
  },

  "responder": {
    "route": "Stairs → Floor 2 → Room 204",
    "instructions": "Allergic to Penicillin"
  },

  "agentStatus": {
    "verifier": "complete",
    "hospitalFinder": "complete",
    "dispatch": "complete"
  }
}
📡 Real-time Channels
/channels/doctor:{doctorId}
{
  "event": "patient_incoming",
  "incidentId": "INC-XXX"
}
/channels/incident:{incidentId}
{
  "event": "incident_update",
  "incidentId": "INC-XXX"
}
/channels/patient:{patientUhid}
{
  "event": "sos_activated",
  "incidentId": "INC-XXX"
}