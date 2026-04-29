import crypto from "node:crypto";
import { nowIso } from "../lib/time.js";

const toIncidentId = (eventId) => {
    const stamp = nowIso().replace(/[\-:TZ.]/g, "").slice(0, 12);
    return `INC-${stamp}-${eventId.slice(0, 6).toUpperCase()}`;
};

const sanitizeList = (value) => (Array.isArray(value) ? value.map((item) => String(item)) : []);

const buildPatientSnapshot = ({ requestPayload, normalized, eventId }) => {
    const incoming = requestPayload?.patient ?? {};
    const uhid = incoming.uhid ?? incoming.id ?? `PAT-${eventId.slice(0, 8).toUpperCase()}`;

    return {
        id: incoming.id ?? uhid,
        uhid,
        name: incoming.name ?? "Unknown Patient",
        age: Number.isFinite(incoming.age) ? Number(incoming.age) : null,
        blood_type: incoming.blood_type ?? "Unknown",
        allergies: sanitizeList(incoming.allergies),
        medications: sanitizeList(incoming.medications),
        conditions: sanitizeList(incoming.conditions),
        critical_flags: sanitizeList(incoming.critical_flags),
        vitals_history: {
            last_spo2: normalized.patient.spo2,
            last_heart_rate: normalized.patient.heart_rate,
            last_recorded: normalized.timestamp,
        },
        imaging: Array.isArray(incoming.imaging) ? incoming.imaging : [],
    };
};

const buildLocation = ({ requestPayload, normalized }) => {
    const incoming = requestPayload?.location ?? {};
    return {
        venue: requestPayload?.venue_id ?? incoming.venue ?? "Unknown Venue",
        room: requestPayload?.node_id ?? incoming.room ?? "N/A",
        floor: incoming.floor ?? "N/A",
        gps: {
            lat: normalized.location.lat,
            lng: normalized.location.lng,
        },
    };
};

export const actionAgent = async ({
    requestPayload,
    normalized,
    classification,
    decision,
    hospitalSelection,
    routing,
    repository,
    realtime,
}) => {
    const eventId = crypto.randomUUID();
    const incidentId = toIncidentId(eventId);
    const idempotencyKey = requestPayload.idempotency_key ?? `${normalized.timestamp}:${normalized.location.lat}:${normalized.location.lng}`;
    const patient = buildPatientSnapshot({ requestPayload, normalized, eventId });
    const location = buildLocation({ requestPayload, normalized });

    const assignedDoctor =
        (await repository.acquireDoctorAssignment({
            hospitalId: hospitalSelection.hospital_id,
            crisisType: classification.type,
            incidentId,
        })) ??
        {
            id: "UNASSIGNED",
            name: "Doctor unavailable",
            specialty: classification.type,
            available: false,
        };

    const agentStatus = {
        verifier: "complete",
        patient_id: "complete",
        hospital_finder: "complete",
        dispatch: "complete",
        comms: "complete",
        evac_router: "complete",
        doctor_assignment: assignedDoctor.id !== "UNASSIGNED" ? "complete" : "blocked",
    };

    const incidentPayload = {
        incidentId,
        incident_id: incidentId,
        status: "active",
        createdAt: nowIso(),
        updatedAt: nowIso(),
        location,
        crisis: {
            type: classification.type,
            subtype: classification.type,
            severity: classification.severity,
            verified: true,
        },
        patient,
        assignedHospital: {
            id: hospitalSelection.hospital_id,
            name: hospitalSelection.hospital_name,
            distance: `${hospitalSelection.distance_km}km`,
            etaAmbulance: `${hospitalSelection.eta} mins`,
        },
        assigned_hospital: {
            id: hospitalSelection.hospital_id,
            name: hospitalSelection.hospital_name,
            distance: `${hospitalSelection.distance_km}km`,
            eta_ambulance: `${hospitalSelection.eta} mins`,
        },
        assignedDoctor: {
            id: assignedDoctor.id,
            name: assignedDoctor.name,
            specialty: assignedDoctor.specialty ?? classification.type,
            available: assignedDoctor.id !== "UNASSIGNED",
        },
        assigned_doctor: {
            id: assignedDoctor.id,
            name: assignedDoctor.name,
            specialty: assignedDoctor.specialty ?? classification.type,
            available: assignedDoctor.id !== "UNASSIGNED",
        },
        responder: {
            route: routing.geometry ? "Follow generated route geometry" : "Proceed via nearest safe path",
            instructions: `Priority ${decision.priority}. ETA ${routing.eta} mins.`,
        },
        agentStatus,
        agent_status: agentStatus,
    };

    const finalDecision = {
        event_id: eventId,
        incident_id: incidentId,
        timestamp: nowIso(),
        classification,
        decision,
        hospital: hospitalSelection,
        routing,
        assigned_doctor: incidentPayload.assigned_doctor,
        patient: incidentPayload.patient,
        incident: incidentPayload,
    };

    await repository.insertEmergencyEvent({
        id: eventId,
        idempotency_key: idempotencyKey,
        source: normalized.source,
        received_at: nowIso(),
        payload: requestPayload,
        normalized,
        classification,
        decision,
        hospital_selection: hospitalSelection,
        routing,
        final_decision: finalDecision,
    });

    await repository.createIncident(incidentPayload);

    const incidentChannel = `incident-${incidentId}`;
    const doctorChannel = `doctor-${incidentPayload.assignedDoctor.id}`;
    const patientChannel = `patient-${patient.uhid}`;

    await repository.publishChannelMessage(incidentChannel, {
        event: "incident_update",
        incidentId,
    });

    if (incidentPayload.assignedDoctor.id !== "UNASSIGNED") {
        await repository.publishChannelMessage(doctorChannel, {
            event: "patient_incoming",
            incidentId,
        });
    }

    await repository.publishChannelMessage(patientChannel, {
        event: "sos_activated",
        incidentId,
    });

    // emit compact summary to stay under Pusher's 10 KB per-event limit;
    // emitToChannel handles auto-compact internally as a second safety net.
    await realtime.emit("emergency.decision.created", {
        event_id: eventId,
        incident_id: incidentId,
        timestamp: finalDecision.timestamp,
        severity: classification.severity,
        priority: decision.priority,
        needs_icu: decision.needs_icu,
        hospital_id: hospitalSelection.hospital_id,
        eta: routing.eta,
    });

    await realtime.emitToChannel(incidentChannel, "incident_update", incidentPayload);

    if (incidentPayload.assignedDoctor.id !== "UNASSIGNED") {
        await realtime.emitToChannel(doctorChannel, "patient_incoming", incidentPayload);
    }

    await realtime.emitToChannel("admin-dashboard", "incident_update", {
        incidentId: finalDecision.event_id,
        doctorId: assignedDoctor.id,
        doctorName: assignedDoctor.name,
        hospitalId: hospitalSelection.hospital_id,
        hospitalName: hospitalSelection.hospital_name,
        patientName: requestPayload?.patient?.name ?? incidentPayload.patient?.name ?? "Unknown",
        severity: classification.severity,
        crisisType: classification.type,
        etaMinutes: routing.eta,
        status: "active",
        timestamp: new Date().toISOString(),
    });

    await realtime.emitToChannel(patientChannel, "sos_activated", incidentPayload);

    return {
        event_id: eventId,
        incident_id: incidentId,
        idempotency_key: idempotencyKey,
        assigned_doctor: incidentPayload.assigned_doctor,
        final_decision: finalDecision,
    };
};
