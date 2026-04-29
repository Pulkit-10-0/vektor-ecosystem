import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { doctorSeed } from "../data/doctors.js";
import { hospitalSeed } from "../data/hospitals.js";
import { nowIso } from "../lib/time.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const toJson = (value) => JSON.stringify(value);

export class Repository {
    constructor(dataLayer, logger, operationalStore) {
        this.dataLayer = dataLayer;
        this.logger = logger;
        this.operationalStore = operationalStore;
    }

    async initializeAuditSchemaAndSeed() {
        if (this.dataLayer.mode === "memory") {
            return;
        }

        const schemaPath = path.join(__dirname, "schema.sql");
        const schemaSql = await fs.readFile(schemaPath, "utf8");
        await this.dataLayer.pool.query(schemaSql);

        // Always delete old hospitals and reseed with current hospitalSeed
        await this.dataLayer.pool.query("DELETE FROM hospitals");

        for (const hospital of hospitalSeed) {
            await this.dataLayer.pool.query(
                `
        INSERT INTO hospitals (id, name, lat, lng, has_icu, specialties, capacity_score)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        `,
                [
                    hospital.id,
                    hospital.name,
                    hospital.lat,
                    hospital.lng,
                    hospital.has_icu,
                    hospital.specialties,
                    hospital.capacity_score,
                ]
            );
        }
    }

    async getHospitalsFromAuditDb() {
        if (this.dataLayer.mode === "memory") {
            return this.dataLayer.store.getHospitals();
        }

        const result = await this.dataLayer.pool.query("SELECT * FROM hospitals ORDER BY id ASC");
        return result.rows;
    }

    async insertEmergencyEventAudit(event) {
        if (this.dataLayer.mode === "memory") {
            return this.dataLayer.store.insertEvent(event);
        }

        const query = {
            text: `
        INSERT INTO emergency_events (
          id, idempotency_key, source, received_at, payload,
          normalized, classification, decision, hospital_selection,
          routing, final_decision
        ) VALUES (
          $1, $2, $3, $4, $5::jsonb,
          $6::jsonb, $7::jsonb, $8::jsonb, $9::jsonb,
          $10::jsonb, $11::jsonb
        )
        ON CONFLICT (idempotency_key) DO NOTHING
      `,
            values: [
                event.id,
                event.idempotency_key,
                event.source,
                event.received_at,
                toJson(event.payload),
                toJson(event.normalized),
                toJson(event.classification),
                toJson(event.decision),
                toJson(event.hospital_selection),
                toJson(event.routing),
                toJson(event.final_decision),
            ],
        };

        await this.dataLayer.pool.query(query);
        return event;
    }

    async insertEmergencyEvent(event) {
        await this.insertEmergencyEventAudit(event);

        if (this.operationalStore) {
            await this.operationalStore.insertEvent(event);
        }

        return event;
    }

    async init() {
        await this.initializeAuditSchemaAndSeed();

        if (this.operationalStore) {
            const hospitals = hospitalSeed;  // Use hardcoded seed, not DB
            await this.operationalStore.init({
                hospitals,
                doctors: doctorSeed,
            });
        }
    }

    async createIncident(incident) {
        if (!this.operationalStore) {
            throw new Error("Operational store is not initialized");
        }
        return this.operationalStore.createIncident(incident);
    }

    async getIncidentById(incidentId) {
        if (!this.operationalStore) {
            return null;
        }
        return this.operationalStore.getIncidentById(incidentId);
    }

    async listIncidents({ limit = 50, status } = {}) {
        if (!this.operationalStore || typeof this.operationalStore.listIncidents !== "function") {
            return [];
        }

        return this.operationalStore.listIncidents({ limit, status });
    }

    async acquireDoctorAssignment({ hospitalId, crisisType, incidentId }) {
        if (!this.operationalStore) {
            return null;
        }
        return this.operationalStore.acquireAvailableDoctor({ hospitalId, crisisType, incidentId });
    }

    async dischargeIncident({ incidentId, currentstatus, payload }) {
        if (!this.operationalStore) {
            return null;
        }
        return this.operationalStore.dischargeIncident({ incidentId, currentstatus, payload });
    }

    async publishChannelMessage(channel, payload) {
        if (this.operationalStore) {
            await this.operationalStore.publishChannelMessage(channel, payload);
        }
    }

    async getHospitals() {
        if (this.operationalStore) {
            const hospitals = await this.operationalStore.getHospitals();
            if (Array.isArray(hospitals) && hospitals.length > 0) {
                return hospitals;
            }
        }

        return this.getHospitalsFromAuditDb();
    }

    async ping() {
        if (this.dataLayer.mode === "memory") {
            return { ok: true, mode: "memory" };
        }

        const result = await this.dataLayer.pool.query("SELECT 1 AS ok");
        return { ok: result.rows[0]?.ok === 1, mode: "postgres" };
    }

    async getRouteCache(cacheKey, maxAgeMinutes = 10) {
        if (this.operationalStore) {
            const cache = await this.operationalStore.getRouteCache(cacheKey, maxAgeMinutes);
            if (cache) {
                return cache;
            }
        }

        if (this.dataLayer.mode === "memory") {
            return this.dataLayer.store.getRouteCache(cacheKey, maxAgeMinutes);
        }

        const result = await this.dataLayer.pool.query(
            `
      SELECT cache_key, distance_km, eta_minutes, geometry, updated_at
      FROM routing_cache
      WHERE cache_key = $1
      AND updated_at > NOW() - ($2::text || ' minutes')::interval
      `,
            [cacheKey, String(maxAgeMinutes)]
        );

        return result.rows[0] ?? null;
    }

    async upsertRouteCache(entry) {
        if (this.operationalStore) {
            await this.operationalStore.upsertRouteCache(entry);
        }

        if (this.dataLayer.mode === "memory") {
            await this.dataLayer.store.upsertRouteCache(entry);
            return;
        }

        await this.dataLayer.pool.query(
            `
      INSERT INTO routing_cache (cache_key, distance_km, eta_minutes, geometry, updated_at)
      VALUES ($1, $2, $3, $4::jsonb, $5)
      ON CONFLICT (cache_key)
      DO UPDATE SET
        distance_km = EXCLUDED.distance_km,
        eta_minutes = EXCLUDED.eta_minutes,
        geometry = EXCLUDED.geometry,
        updated_at = EXCLUDED.updated_at
      `,
            [entry.cache_key, entry.distance_km, entry.eta_minutes, toJson(entry.geometry), nowIso()]
        );
    }

    async close() {
        if (this.dataLayer.mode === "postgres") {
            await this.dataLayer.pool.end();
        }
    }
}
