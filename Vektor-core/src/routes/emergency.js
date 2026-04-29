import { runEmergencyPipeline } from "../pipeline/runEmergencyPipeline.js";
import { feedbackAgent } from "../agents/feedbackAgent.js";

const emergencyBodySchema = {
    type: "object",
    additionalProperties: true,
    properties: {
        source: { type: "string" },
        timestamp: { type: "string" },
        idempotency_key: { type: "string" },
        location: {
            type: "object",
            properties: {
                lat: { type: "number" },
                lng: { type: "number" },
            },
            required: ["lat", "lng"],
        },
        patient: {
            type: "object",
            properties: {
                spo2: { type: "number" },
                heart_rate: { type: "number" },
                symptoms: { type: "array", items: { type: "string" } },
            },
            required: ["spo2", "heart_rate"],
        },
    },
    required: ["location", "patient"],
};

export const emergencyRoutes = async (fastify) => {
    fastify.post(
        "/api/emergency",
        {
            schema: {
                body: emergencyBodySchema,
            },
        },
        async (request, reply) => {
            const result = await runEmergencyPipeline({
                payload: request.body,
                repository: fastify.repository,
                aiClient: fastify.aiClient,
                routingClient: fastify.routingClient,
                realtime: fastify.realtime,
                logger: request.log,
            });

            return reply.code(200).send(result);
        }
    );

    fastify.post(
        "/api/offline-sync",
        {
            schema: {
                body: {
                    type: "object",
                    properties: {
                        offline_decision: { type: "object" },
                        payload: emergencyBodySchema,
                    },
                    required: ["offline_decision", "payload"],
                },
            },
        },
        async (request, reply) => {
            const online = await runEmergencyPipeline({
                payload: request.body.payload,
                repository: fastify.repository,
                aiClient: fastify.aiClient,
                routingClient: fastify.routingClient,
                realtime: fastify.realtime,
                logger: request.log,
            });

            const feedback = feedbackAgent({
                offlineDecision: request.body.offline_decision,
                onlineDecision: {
                    decision: online.decision,
                    hospital: online.hospital,
                },
            });

            await fastify.realtime.emit("emergency.decision.sync", {
                event_id: online.event_id,
                feedback,
            });

            return reply.send({
                synced: true,
                online_decision: online,
                feedback,
            });
        }
    );

    fastify.get("/api/incidents/:incidentId", async (request, reply) => {
        const incidentId = String(request.params.incidentId ?? "");
        const incident = await fastify.repository.getIncidentById(incidentId);
        if (!incident) {
            return reply.code(404).send({
                error: "incident_not_found",
                message: `Incident ${incidentId} not found`,
            });
        }

        return reply.send({ incident });
    });

    fastify.get("/api/incidents", async (request, reply) => {
        const limitRaw = request.query?.limit;
        const statusRaw = request.query?.status;
        const limit = typeof limitRaw === "string" ? Number(limitRaw) : 50;
        const status = typeof statusRaw === "string" && statusRaw.length > 0 ? statusRaw : undefined;

        const incidents = await fastify.repository.listIncidents({ limit, status });
        return reply.send({ incidents });
    });

    fastify.post(
        "/api/incidents/:incidentId/discharge",
        {
            schema: {
                body: {
                    type: "object",
                    additionalProperties: true,
                    properties: {
                        currentstatus: { type: "string" },
                        released_by: { type: "string" },
                        notes: { type: "string" },
                    },
                },
            },
        },
        async (request, reply) => {
            const incidentId = String(request.params.incidentId ?? "");
            const updated = await fastify.repository.dischargeIncident({
                incidentId,
                currentstatus: request.body?.currentstatus ?? "discharged",
                payload: {
                    released_by: request.body?.released_by ?? "frontend",
                    notes: request.body?.notes ?? null,
                },
            });

            if (!updated) {
                return reply.code(404).send({
                    error: "incident_not_found",
                    message: `Incident ${incidentId} not found`,
                });
            }

            await fastify.repository.publishChannelMessage(`incident-${incidentId}`, {
                event: "incident_update",
                incidentId,
                status: "resolved",
            });

            await fastify.realtime.emitToChannel(`incident-${incidentId}`, "incident_update", updated);

            const doctorId = updated?.assignedDoctor?.id ?? updated?.assigned_doctor?.id;
            if (doctorId && doctorId !== "UNASSIGNED") {
                await fastify.repository.publishChannelMessage(`doctor-${doctorId}`, {
                    event: "doctor_available",
                    incidentId,
                    status: "available",
                });
                await fastify.realtime.emitToChannel(`doctor-${doctorId}`, "doctor_available", {
                    doctorId,
                    status: "available",
                    incidentId,
                });
            }

            await fastify.realtime.emitToChannel("admin-dashboard", "incident_update", {
                incidentId,
                doctorId,
                status: "resolved",
                timestamp: new Date().toISOString(),
            });

            return reply.send({
                updated: true,
                incident: updated,
            });
        }
    );
};
