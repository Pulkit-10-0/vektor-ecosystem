import Fastify from "fastify";
import cors from "@fastify/cors";

import { createDataLayer } from "./db/connection.js";
import { Repository } from "./db/repository.js";
import { AIClient } from "./services/aiClient.js";
import { RoutingClient } from "./services/routingClient.js";
import { RealtimeService } from "./services/realtimeService.js";
import { FirebaseOperationalStore } from "./db/firebaseOperationalStore.js";
import { env } from "./config/env.js";
import { publicRoutes } from "./routes/public.js";
import { emergencyRoutes } from "./routes/emergency.js";
import { healthRoutes } from "./routes/health.js";

export const buildApp = async () => {
    const app = Fastify({
        logger: true,
        trustProxy: true,
        bodyLimit: 64 * 1024,
        requestIdHeader: "x-request-id",
    });

    await app.register(cors, { origin: true });

    const dataLayer = createDataLayer(app.log);
    const operationalStore = new FirebaseOperationalStore(app.log, env);
    const repository = new Repository(dataLayer, app.log, operationalStore);
    const aiClient = new AIClient(app.log);
    const routingClient = new RoutingClient(app.log);
    const realtime = new RealtimeService(app.log);

    await repository.init();

    app.decorate("repository", repository);
    app.decorate("aiClient", aiClient);
    app.decorate("routingClient", routingClient);
    app.decorate("realtime", realtime);
    app.decorate("operationalStore", operationalStore);

    app.addHook("onClose", async () => {
        await repository.close();
    });

    app.addHook("onRequest", async (request, reply) => {
        reply.header("x-service-name", "vectorgo-agent-engine");
    });

    app.setErrorHandler((error, request, reply) => {
        if (error.validation) {
            reply.status(400).send({
                error: "validation_error",
                message: "Request body failed validation",
                details: error.validation,
            });
            return;
        }

        if (error.code === "FST_ERR_CTP_BODY_TOO_LARGE") {
            reply.status(413).send({
                error: "payload_too_large",
                message: "Request payload exceeds the configured limit",
            });
            return;
        }

        request.log.error({ err: error }, "Request failed");
        reply.status(500).send({
            error: "internal_error",
            message: error.message,
        });
    });

    await app.register(healthRoutes);
    await app.register(publicRoutes);
    await app.register(emergencyRoutes);

    return app;
};
