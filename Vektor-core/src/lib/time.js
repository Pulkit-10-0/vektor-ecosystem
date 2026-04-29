export const nowIso = () => new Date().toISOString();

export const withTimeout = async (promiseFactory, timeoutMs, timeoutMessage) => {
    let timeoutId;
    const timeoutPromise = new Promise((_, reject) => {
        timeoutId = setTimeout(() => {
            reject(new Error(timeoutMessage ?? `Operation timed out after ${timeoutMs}ms`));
        }, timeoutMs);
    });

    try {
        return await Promise.race([promiseFactory(), timeoutPromise]);
    } finally {
        clearTimeout(timeoutId);
    }
};

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
