export const healthRoutes = async (fastify) => {
    fastify.get("/health", async () => {
        return { status: "ok", service: "vectorgo-agent-engine" };
    });

    fastify.get("/api/ping", async () => {
        return { status: "ok", service: "vectorgo-agent-engine", route: "ping" };
    });

    fastify.get("/ready", async () => {
        const database = await fastify.repository.ping();

        return {
            status: "ready",
            service: "vectorgo-agent-engine",
            database,
            integrations: {
                gemini: fastify.aiClient.hasGemini(),
                openrouter: fastify.aiClient.hasOpenRouter(),
                ors: fastify.routingClient.isConfigured(),
                realtime: fastify.realtime.isConfigured(),
                firebase: fastify.operationalStore.isConfigured(),
            },
        };
    });
};
