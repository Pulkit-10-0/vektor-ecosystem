import { hospitalSeed } from "../data/hospitals.js";

export class MemoryStore {
    constructor() {
        this.events = [];
        this.hospitals = [...hospitalSeed];
        this.cachedRouting = new Map();
    }

    async insertEvent(event) {
        this.events.push(event);
        return event;
    }

    async getHospitals() {
        return this.hospitals;
    }

    async upsertRouteCache(cacheEntry) {
        this.cachedRouting.set(cacheEntry.cache_key, cacheEntry);
    }

    async getRouteCache(cacheKey, maxAgeMinutes = 10) {
        const row = this.cachedRouting.get(cacheKey);
        if (!row) {
            return null;
        }

        const ageMs = Date.now() - new Date(row.updated_at).getTime();
        if (ageMs > maxAgeMinutes * 60 * 1000) {
            return null;
        }

        return row;
    }
}
