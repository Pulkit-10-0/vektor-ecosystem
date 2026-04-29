import { nowIso } from "../lib/time.js";

export const intakeAgent = (input) => {
    const location = input?.location ?? {};
    const hasValidLocation =
        Number.isFinite(location.lat) &&
        Number.isFinite(location.lng) &&
        Math.abs(location.lat) <= 90 &&
        Math.abs(location.lng) <= 180;

    const timestamp = input?.timestamp ? new Date(input.timestamp) : new Date();
    const validTimestamp = Number.isFinite(timestamp.getTime());

    const normalized = {
        source: input?.source ?? "app",
        timestamp: validTimestamp ? timestamp.toISOString() : nowIso(),
        patient: {
            spo2: Number.isFinite(input?.patient?.spo2) ? Number(input.patient.spo2) : 95,
            heart_rate: Number.isFinite(input?.patient?.heart_rate) ? Number(input.patient.heart_rate) : 80,
            symptoms: Array.isArray(input?.patient?.symptoms) ? input.patient.symptoms : [],
        },
        location: hasValidLocation
            ? { lat: Number(location.lat), lng: Number(location.lng) }
            : { lat: 0, lng: 0 },
    };

    return {
        normalized,
        confidence: hasValidLocation && validTimestamp ? 0.95 : 0.9,
        validation: {
            location_present: hasValidLocation,
            timestamp_valid: validTimestamp,
        },
    };
};
