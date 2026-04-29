import Pusher from "pusher";
import { env } from "../config/env.js";

const PUSHER_MAX_EVENT_BYTES = 10 * 1024;

const byteSize = (value) => Buffer.byteLength(JSON.stringify(value), "utf8");

const toCompactPayload = (payload) => {
    const source = payload && typeof payload === "object" ? payload : {};
    return {
        event_id: source.event_id ?? source.eventId ?? null,
        incident_id: source.incident_id ?? source.incidentId ?? null,
        patient_id: source.patient?.uhid ?? source.patient_id ?? null,
        status: source.status ?? null,
        timestamp: source.timestamp ?? source.updatedAt ?? source.createdAt ?? null,
        meta: {
            truncated_for_realtime: true,
        },
    };
};

export class RealtimeService {
    constructor(logger) {
        this.logger = logger;
        this.pusher = this.createPusherClient();
    }

    isConfigured() {
        return Boolean(this.pusher);
    }

    createPusherClient() {
        if (!env.pusherAppId || !env.pusherKey || !env.pusherSecret || !env.pusherCluster) {
            return null;
        }

        return new Pusher({
            appId: env.pusherAppId,
            key: env.pusherKey,
            secret: env.pusherSecret,
            cluster: env.pusherCluster,
            useTLS: true,
        });
    }

    async emit(eventName, payload) {
        return this.emitToChannel("vectorgo-events", eventName, payload);
    }

    async emitToChannel(channelName, eventName, payload) {
        const payloadSize = byteSize(payload);

        if (!this.pusher) {
            this.logger.info({ channelName, eventName, payload }, "Realtime event emitted (log fallback)");
            return;
        }

        const primaryPayload = payloadSize > PUSHER_MAX_EVENT_BYTES ? toCompactPayload(payload) : payload;

        if (payloadSize > PUSHER_MAX_EVENT_BYTES) {
            this.logger.warn(
                { channelName, eventName, payloadSize, maxBytes: PUSHER_MAX_EVENT_BYTES },
                "Realtime payload exceeded Pusher max size; sending compact payload",
            );
        }

        try {
            await this.pusher.trigger(channelName, eventName, primaryPayload);
        } catch (error) {
            const status = error?.status;

            if (status === 413 && primaryPayload === payload) {
                const fallbackPayload = toCompactPayload(payload);
                const fallbackSize = byteSize(fallbackPayload);

                if (fallbackSize <= PUSHER_MAX_EVENT_BYTES) {
                    try {
                        await this.pusher.trigger(channelName, eventName, fallbackPayload);
                        this.logger.warn(
                            { channelName, eventName, payloadSize, fallbackSize },
                            "Realtime event retried with compact payload after 413",
                        );
                        return;
                    } catch (retryError) {
                        this.logger.error(
                            {
                                channelName,
                                eventName,
                                payloadSize,
                                fallbackSize,
                                err: retryError,
                            },
                            "Realtime retry with compact payload failed",
                        );
                        return;
                    }
                }
            }

            this.logger.error(
                {
                    channelName,
                    eventName,
                    payloadSize,
                    status,
                    err: error,
                },
                "Realtime emit failed; continuing without blocking request",
            );
        }
    }
}
