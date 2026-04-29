import { withTimeout } from "../lib/time.js";

const classifyByRules = (normalized) => {
    const spo2 = normalized.patient.spo2;
    const hr = normalized.patient.heart_rate;
    const symptoms = normalized.patient.symptoms.map((item) => String(item).toLowerCase());

    let severity = "medium";
    let type = "general";
    let confidence = 0.75;

    if (spo2 < 90 || hr > 130) {
        severity = "critical";
        confidence = 0.9;
    } else if (spo2 < 93 || hr > 115) {
        severity = "high";
        confidence = 0.82;
    } else if (hr > 100) {
        severity = "elevated";
        confidence = 0.78;
    }

    if (symptoms.includes("breathlessness") || symptoms.includes("low_spo2") || spo2 < 93) {
        type = "respiratory";
    } else if (symptoms.includes("chest_pain") || hr > 125) {
        type = "cardiac";
    } else if (symptoms.includes("trauma") || symptoms.includes("bleeding")) {
        type = "trauma";
    }

    return { severity, type, confidence, source: "rules" };
};

export const classificationAgent = async ({ normalized }) => {
    return withTimeout(async () => classifyByRules(normalized), 300, "Classification timeout");
};
