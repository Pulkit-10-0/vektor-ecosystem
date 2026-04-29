import { env } from "../config/env.js";

export class RoutingClient {
    isConfigured() {
        return Boolean(env.orsApiKey);
    }

    async getRoute(from, to) {
        if (!env.orsApiKey) {
            throw new Error("ORS_API_KEY is not configured");
        }

        const response = await fetch("https://api.openrouteservice.org/v2/directions/driving-car/geojson", {
            method: "POST",
            headers: {
                Authorization: env.orsApiKey,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                coordinates: [
                    [from.lng, from.lat],
                    [to.lng, to.lat],
                ],
            }),
        });

        if (!response.ok) {
            throw new Error(`OpenRouteService request failed with status ${response.status}`);
        }

        const body = await response.json();
        const feature = body?.features?.[0];
        const summary = feature?.properties?.summary;
        if (!summary) {
            throw new Error("OpenRouteService response missing route summary");
        }

        return {
            distanceKm: Number(summary.distance) / 1000,
            etaMinutes: Math.max(1, Math.round(Number(summary.duration) / 60)),
            geometry: feature.geometry,
        };
    }
}
