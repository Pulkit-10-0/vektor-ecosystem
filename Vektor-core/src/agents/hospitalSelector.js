import { haversineKm } from "../lib/geo.js";

const hasSpecialty = (hospital, type) => hospital.specialties?.includes(type);

export const hospitalSelectorAgent = ({ hospitals, location, decision, classification }) => {
    if (!Array.isArray(hospitals) || hospitals.length === 0) {
        throw new Error("No hospitals available");
    }

    const scored = hospitals
        .map((hospital) => {
            const distanceKm = haversineKm(location, { lat: hospital.lat, lng: hospital.lng });
            const capabilityScore = (decision.needs_icu ? (hospital.has_icu ? 1 : 0.4) : 0.8) * 0.45;
            const specialtyScore = hasSpecialty(hospital, classification.type) ? 0.25 : 0.1;
            const capacityScore = Number(hospital.capacity_score ?? 0.5) * 0.2;
            const distanceScore = Math.max(0, 1 - distanceKm / 25) * 0.1;

            const score = capabilityScore + specialtyScore + capacityScore + distanceScore;

            return {
                hospital_id: hospital.id,
                hospital_name: hospital.name,
                eta: Math.max(1, Math.round((distanceKm / 35) * 60)),
                distance_km: Number(distanceKm.toFixed(2)),
                score: Number(score.toFixed(3)),
            };
        })
        .sort((a, b) => b.score - a.score);

    return scored[0];
};
