import { intakeAgent } from "../agents/intakeAgent.js";
import { classificationAgent } from "../agents/classificationAgent.js";
import { decisionAgent } from "../agents/decisionAgent.js";
import { hospitalSelectorAgent } from "../agents/hospitalSelector.js";
import { routingAgent } from "../agents/routingAgent.js";
import { actionAgent } from "../agents/actionAgent.js";
import { env } from "../config/env.js";

const elapsedMs = (start) => Number(process.hrtime.bigint() - start) / 1_000_000;

export const runEmergencyPipeline = async ({ payload, repository, aiClient, routingClient, realtime, logger }) => {
    const totalStart = process.hrtime.bigint();

    const intakeStart = process.hrtime.bigint();
    const intake = intakeAgent(payload);
    const intakeMs = elapsedMs(intakeStart);

    const classificationStart = process.hrtime.bigint();
    const classification = await classificationAgent({ normalized: intake.normalized });
    const classificationMs = elapsedMs(classificationStart);

    const decisionStart = process.hrtime.bigint();
    const decision = await decisionAgent({
        normalized: intake.normalized,
        classification,
        aiClient,
        logger,
    });
    const decisionMs = elapsedMs(decisionStart);

    const hospitals = await repository.getHospitals();
    const selectionStart = process.hrtime.bigint();
    const hospitalSelection = hospitalSelectorAgent({
        hospitals,
        location: intake.normalized.location,
        decision,
        classification,
    });

    const selectedHospital = hospitals.find((hospital) => hospital.id === hospitalSelection.hospital_id);

    const routing = await routingAgent({
        from: intake.normalized.location,
        to: { lat: selectedHospital.lat, lng: selectedHospital.lng },
        routingClient,
        repository,
        logger,
    });

    const selectionAndRoutingMs = elapsedMs(selectionStart);

    const actionResult = await actionAgent({
        requestPayload: payload,
        normalized: intake.normalized,
        classification,
        decision,
        hospitalSelection,
        routing,
        repository,
        realtime,
    });

    const totalMs = elapsedMs(totalStart);

    const response = {
        event_id: actionResult.event_id,
        incident_id: actionResult.incident_id,
        idempotency_key: actionResult.idempotency_key,
        normalized: {
            normalized: true,
            confidence: intake.confidence,
            validation: intake.validation,
        },
        classification,
        decision,
        hospital: hospitalSelection,
        assigned_doctor: actionResult.assigned_doctor,
        routing,
        timing_ms: {
            intake: Number(intakeMs.toFixed(1)),
            classification: Number(classificationMs.toFixed(1)),
            decision: Number(decisionMs.toFixed(1)),
            selection_routing: Number(selectionAndRoutingMs.toFixed(1)),
            total: Number(totalMs.toFixed(1)),
        },
        constraints: {
            total_latency_budget_ms: 3000,
            under_budget: totalMs < 3000,
        },
    };

    const responseSize = Buffer.byteLength(JSON.stringify(response), "utf8");
    if (responseSize > env.maxResponseBytes) {
        return {
            event_id: actionResult.event_id,
            incident_id: actionResult.incident_id,
            idempotency_key: actionResult.idempotency_key,
            classification,
            decision,
            hospital: {
                hospital_id: hospitalSelection.hospital_id,
                hospital_name: hospitalSelection.hospital_name,
                eta: hospitalSelection.eta,
                distance_km: hospitalSelection.distance_km,
                score: hospitalSelection.score,
            },
            assigned_doctor: actionResult.assigned_doctor,
            routing: {
                source: routing.source,
                distance_km: routing.distance_km,
                eta: routing.eta,
            },
            meta: {
                trimmed_for_payload_limit: true,
                max_bytes: env.maxResponseBytes,
            },
        };
    }

    return response;
};
