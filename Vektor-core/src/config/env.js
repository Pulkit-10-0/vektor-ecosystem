import dotenv from "dotenv";

dotenv.config();

const toNumber = (value, fallback) => {
    if (value === undefined || value === null || value === "") {
        return fallback;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

export const env = {
    nodeEnv: process.env.NODE_ENV ?? "development",
    port: toNumber(process.env.PORT, 3000),
    host: process.env.HOST ?? "0.0.0.0",

    databaseUrl: process.env.DATABASE_URL,

    geminiApiKey: process.env.GEMINI_API_KEY,
    geminiModel: process.env.GEMINI_MODEL ?? "gemini-1.5-flash",

    openRouterApiKey: process.env.OPENROUTER_API_KEY,
    openRouterModel: process.env.OPENROUTER_MODEL ?? "openai/gpt-4o-mini",

    orsApiKey: process.env.ORS_API_KEY,

    pusherAppId: process.env.PUSHER_APP_ID,
    pusherKey: process.env.PUSHER_KEY,
    pusherSecret: process.env.PUSHER_SECRET,
    pusherCluster: process.env.PUSHER_CLUSTER,

    firebaseDatabaseUrl: process.env.FIREBASE_DATABASE_URL,
    firebaseProjectId: process.env.FIREBASE_PROJECT_ID,
    firebaseClientEmail: process.env.FIREBASE_CLIENT_EMAIL,
    firebasePrivateKey: process.env.FIREBASE_PRIVATE_KEY,

    maxResponseBytes: toNumber(process.env.MAX_RESPONSE_BYTES, 5 * 1024),
};

export const isProd = env.nodeEnv === "production";
