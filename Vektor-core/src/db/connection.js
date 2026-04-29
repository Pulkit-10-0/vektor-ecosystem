import pg from "pg";
import { env } from "../config/env.js";
import { MemoryStore } from "./memoryStore.js";

const { Pool } = pg;

export const createDataLayer = (logger) => {
    if (!env.databaseUrl) {
        logger.warn("DATABASE_URL is not set. Using in-memory data layer.");
        return { mode: "memory", store: new MemoryStore() };
    }

    const pool = new Pool({ connectionString: env.databaseUrl, ssl: { rejectUnauthorized: false } });

    pool.on("error", (error) => {
        logger.error({ err: error }, "PostgreSQL pool error");
    });

    return { mode: "postgres", pool };
};
