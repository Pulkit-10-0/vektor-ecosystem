import { withTimeout } from "../lib/time.js";
import { withRetry } from "../lib/retry.js";

const ruleDecision = ({ normalized, classification }) => {
    const spo2 = normalized.patient.spo2;
    const hr = normalized.patient.heart_rate;

    const needsIcu = classification.severity === "critical" || spo2 < 90;
    const priority = classification.severity === "critical" ? "high" : "normal";
    const strategy = needsIcu ? "best_equipped" : "nearest_adequate";

    return {
        needs_icu: needsIcu,
        priority,
        strategy,
        confidence: 0.86,
        reason: `rule_engine: severity=${classification.severity}, spo2=${spo2}, hr=${hr}`,
        source: "rule_engine",
    };
};

const sanitizeDecision = (candidate, fallback) => {
    if (!candidate || typeof candidate !== "object") {
        return fallback;
    }

    return {
        needs_icu: Boolean(candidate.needs_icu),
        priority: ["high", "normal", "low"].includes(candidate.priority) ? candidate.priority : fallback.priority,
        strategy: typeof candidate.strategy === "string" && candidate.strategy.length > 0 ? candidate.strategy : fallback.strategy,
        confidence: Number.isFinite(candidate.confidence) ? Number(candidate.confidence) : fallback.confidence,
        reason: typeof candidate.reason === "string" && candidate.reason.length > 0 ? candidate.reason.slice(0, 200) : fallback.reason,
        source: candidate.source ?? "ai",
    };
};

export const decisionAgent = async ({ normalized, classification, aiClient, logger }) => {
    const fallback = ruleDecision({ normalized, classification });

    const aiInput = {
        patient: normalized.patient,
        classification,
        constraints: {
            max_tokens: 300,
            output_format: "strict_json",
        },
    };

    if (!aiClient.hasGemini() && !aiClient.hasOpenRouter()) {
        return fallback;
    }

    try {
        const decision = await withTimeout(
            () =>
                withRetry({
                    retries: 2,
                    fn: async () => {
                        if (aiClient.hasGemini()) {
                            try {
                                const gemini = await aiClient.geminiDecision(aiInput);
                                return { ...sanitizeDecision(gemini, fallback), source: "gemini" };
                            } catch (geminiError) {
                                logger.warn({ err: geminiError }, "Gemini failed, trying OpenRouter fallback");
                            }
                        }

                        if (aiClient.hasOpenRouter()) {
                            const secondary = await aiClient.openRouterDecision(aiInput);
                            return { ...sanitizeDecision(secondary, fallback), source: "openrouter" };
                        }

                        return fallback;
                    },
                }),
            1500,
            "Decision timeout"
        );

        return decision;
    } catch (error) {
        logger.warn({ err: error }, "AI decision failed, using rule fallback");
        return fallback;
    }
};
