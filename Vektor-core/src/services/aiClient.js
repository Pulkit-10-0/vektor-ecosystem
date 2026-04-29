import { env } from "../config/env.js";

const parseJsonFromText = (text) => {
    if (!text || typeof text !== "string") {
        throw new Error("AI response text is empty");
    }

    const trimmed = text.trim();
    try {
        return JSON.parse(trimmed);
    } catch {
        const start = trimmed.indexOf("{");
        const end = trimmed.lastIndexOf("}");
        if (start === -1 || end === -1 || end <= start) {
            throw new Error("AI response was not valid JSON");
        }
        return JSON.parse(trimmed.slice(start, end + 1));
    }
};

export class AIClient {
    constructor(logger) {
        this.logger = logger;
    }

    hasGemini() {
        return Boolean(env.geminiApiKey);
    }

    hasOpenRouter() {
        return Boolean(env.openRouterApiKey);
    }

    async geminiDecision(input) {
        if (!env.geminiApiKey) {
            throw new Error("GEMINI_API_KEY is not configured");
        }

        const url = `https://generativelanguage.googleapis.com/v1beta/models/${env.geminiModel}:generateContent?key=${env.geminiApiKey}`;

        const prompt = [
            "You are an emergency triage decision engine.",
            "Return strictly JSON with keys: needs_icu, priority, strategy, confidence, reason.",
            "Do not include markdown or any extra keys.",
            "Input:",
            JSON.stringify(input),
        ].join("\n");

        const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                generationConfig: { temperature: 0.1, maxOutputTokens: 300 },
                contents: [{ parts: [{ text: prompt }] }],
            }),
        });

        if (!response.ok) {
            throw new Error(`Gemini request failed with status ${response.status}`);
        }

        const body = await response.json();
        const text = body?.candidates?.[0]?.content?.parts?.[0]?.text;
        return parseJsonFromText(text);
    }

    async openRouterDecision(input) {
        if (!env.openRouterApiKey) {
            throw new Error("OPENROUTER_API_KEY is not configured");
        }

        const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${env.openRouterApiKey}`,
            },
            body: JSON.stringify({
                model: env.openRouterModel,
                response_format: { type: "json_object" },
                max_tokens: 300,
                temperature: 0.1,
                messages: [
                    {
                        role: "system",
                        content:
                            "Return only JSON with keys: needs_icu, priority, strategy, confidence, reason.",
                    },
                    { role: "user", content: JSON.stringify(input) },
                ],
            }),
        });

        if (!response.ok) {
            throw new Error(`OpenRouter request failed with status ${response.status}`);
        }

        const body = await response.json();
        const text = body?.choices?.[0]?.message?.content;
        return parseJsonFromText(text);
    }
}
