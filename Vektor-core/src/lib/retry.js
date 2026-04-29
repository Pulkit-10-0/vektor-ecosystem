import { sleep } from "./time.js";

export const withRetry = async ({ fn, retries = 2, baseDelayMs = 80, factor = 2 }) => {
    let lastError;

    for (let attempt = 0; attempt <= retries; attempt += 1) {
        try {
            return await fn(attempt);
        } catch (error) {
            lastError = error;
            if (attempt === retries) {
                break;
            }
            const delay = baseDelayMs * (factor ** attempt);
            await sleep(delay);
        }
    }

    throw lastError;
};
