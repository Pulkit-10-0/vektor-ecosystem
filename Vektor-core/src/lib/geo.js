const EARTH_RADIUS_KM = 6371;

const toRadians = (degrees) => (degrees * Math.PI) / 180;

export const haversineKm = (from, to) => {
    const dLat = toRadians(to.lat - from.lat);
    const dLng = toRadians(to.lng - from.lng);
    const lat1 = toRadians(from.lat);
    const lat2 = toRadians(to.lat);

    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_KM * c;
};

export const estimateEtaMinutes = (distanceKm, averageSpeedKmh = 35) => {
    if (distanceKm <= 0) {
        return 1;
    }
    const etaHours = distanceKm / averageSpeedKmh;
    return Math.max(1, Math.round(etaHours * 60));
};
