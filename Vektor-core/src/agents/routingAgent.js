import { estimateEtaMinutes, haversineKm } from "../lib/geo.js";

const routeKey = (from, to) => `${from.lat.toFixed(4)},${from.lng.toFixed(4)}:${to.lat.toFixed(4)},${to.lng.toFixed(4)}`;

export const routingAgent = async ({ from, to, routingClient, repository, logger }) => {
    const cacheKey = routeKey(from, to);
    const cached = await repository.getRouteCache(cacheKey, 15);
    if (cached) {
        return {
            source: "cache",
            distance_km: Number(cached.distance_km),
            eta: Number(cached.eta_minutes),
            geometry: cached.geometry,
        };
    }

    try {
        const route = await routingClient.getRoute(from, to);
        const response = {
            source: "openrouteservice",
            distance_km: Number(route.distanceKm.toFixed(2)),
            eta: route.etaMinutes,
            geometry: route.geometry,
        };

        await repository.upsertRouteCache({
            cache_key: cacheKey,
            distance_km: response.distance_km,
            eta_minutes: response.eta,
            geometry: response.geometry,
            updated_at: new Date().toISOString(),
        });

        return response;
    } catch (error) {
        logger.warn({ err: error }, "OpenRouteService failed, using Haversine fallback");
        const distanceKm = haversineKm(from, to);
        return {
            source: "haversine",
            distance_km: Number(distanceKm.toFixed(2)),
            eta: estimateEtaMinutes(distanceKm),
            geometry: null,
        };
    }
};
