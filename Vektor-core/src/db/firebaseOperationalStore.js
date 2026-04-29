import admin from "firebase-admin";
import { doctorSeed } from "../data/doctors.js";
import { hospitalSeed } from "../data/hospitals.js";
import { nowIso } from "../lib/time.js";
import crypto from "crypto";


function makeSafeKey(key) {
  return crypto.createHash("md5").update(key).digest("hex");
}

const toMillis = (isoString) => {
    const value = new Date(isoString).getTime();
    return Number.isFinite(value) ? value : Date.now();
};

const isAvailableDoctor = (doctor) => {
    const currentStatus = doctor?.currentstatus ?? doctor?.status ?? "available";
    return currentStatus === "available" && !doctor?.currentIncidentId;
};

const specialtyAliases = {
    cardiac: ["cardiac", "cardiology", "cardiologist"],
    respiratory: ["respiratory", "pulmonology", "pulmonologist"],
    trauma: ["trauma", "emergency", "critical-care"],
    general: ["general", "internal-medicine"],
};

const matchesSpecialty = (doctorSpecialty, requestedType) => {
    const doctor = String(doctorSpecialty ?? "").toLowerCase();
    const target = String(requestedType ?? "general").toLowerCase();
    const aliases = specialtyAliases[target] ?? [target];
    return aliases.includes(doctor);
};

export class FirebaseOperationalStore {
    constructor(logger, env) {
        this.logger = logger;
        this.env = env;
        this.mode = "memory";
        this.db = null;
        this.memory = {
            doctors: {},
            incidents: {},
            channels: {},
            hospitals: [...hospitalSeed],
            routeCache: new Map(),
            events: [],
        };
    }

    isConfigured() {
        return this.mode === "firebase";
    }

    async init({ hospitals = hospitalSeed, doctors = doctorSeed } = {}) {
        const hasFirebaseConfig =
            Boolean(this.env.firebaseDatabaseUrl) &&
            Boolean(this.env.firebaseProjectId) &&
            Boolean(this.env.firebaseClientEmail) &&
            Boolean(this.env.firebasePrivateKey);

        if (!hasFirebaseConfig) {
            this.seedMemory({ hospitals, doctors });
            this.logger.warn("Firebase Realtime DB is not fully configured. Using in-memory operational store.");
            return;
        }

        try {
            if (admin.apps.length === 0) {
                admin.initializeApp({
                    credential: admin.credential.cert({
                        projectId: this.env.firebaseProjectId,
                        clientEmail: this.env.firebaseClientEmail,
                        privateKey: this.env.firebasePrivateKey.replace(/\\n/g, "\n"),
                    }),
                    databaseURL: this.env.firebaseDatabaseUrl,
                });
            }

            this.db = admin.database();
            this.mode = "firebase";
            await this.ensureSeedData({ hospitals, doctors });
            this.logger.info("Firebase operational store initialized");
        } catch (error) {
            this.seedMemory({ hospitals, doctors });
            this.mode = "memory";
            this.db = null;
            this.logger.error({ err: error }, "Failed to initialize Firebase. Falling back to in-memory operational store.");
        }
    }

    seedMemory({ hospitals, doctors }) {
        this.memory.hospitals = [...hospitals];
        this.memory.doctors = {};
        this.memory.incidents = {};
        this.memory.channels = {};
        this.memory.routeCache = new Map();
        this.memory.events = [];
        for (const doctor of doctors) {
            this.memory.doctors[doctor.id] = {
                ...doctor,
                lastUpdated: nowIso(),
            };
        }
    }

    async ensureSeedData({ hospitals, doctors }) {
        const hospitalRef = this.db.ref("hospitals");
        const doctorsRef = this.db.ref("doctors");

        // Always overwrite with fresh seed data
        const hospitalPayload = Object.fromEntries(
            hospitals.map((hospital) => [
                hospital.id,
                {
                    ...hospital,
                    updatedAt: nowIso(),
                },
            ])
        );
        await hospitalRef.set(hospitalPayload);

        const doctorPayload = Object.fromEntries(
            doctors.map((doctor) => [
                doctor.id,
                {
                    ...doctor,
                    lastUpdated: nowIso(),
                },
            ])
        );
        await doctorsRef.set(doctorPayload);
    }

    async getHospitals() {
        if (this.mode !== "firebase") {
            return this.memory.hospitals;
        }

        const snapshot = await this.db.ref("hospitals").get();
        const hospitals = snapshot.val() ?? {};
        return Object.values(hospitals);
    }

    async insertEvent(event) {
        if (this.mode !== "firebase") {
            this.memory.events.push(event);
            return;
        }

        await this.db.ref(`events/${event.id}`).set({
            ...event,
            replicatedAt: nowIso(),
        });
    }

    async createIncident(incident) {
        if (this.mode !== "firebase") {
            this.memory.incidents[incident.incidentId] = incident;
            return incident;
        }

        await this.db.ref(`incidents/${incident.incidentId}`).set(incident);
        return incident;
    }

    async getIncidentById(incidentId) {
        if (this.mode !== "firebase") {
            return this.memory.incidents[incidentId] ?? null;
        }

        const snapshot = await this.db.ref(`incidents/${incidentId}`).get();
        return snapshot.exists() ? snapshot.val() : null;
    }

    async listIncidents({ limit = 50, status } = {}) {
        const take = Number(limit);
        const max = Number.isFinite(take) ? Math.min(200, Math.max(1, take)) : 50;

        if (this.mode !== "firebase") {
            const rows = Object.values(this.memory.incidents);
            const filtered = status ? rows.filter((row) => String(row?.status) === String(status)) : rows;
            filtered.sort((a, b) => {
                const ta = new Date(a?.createdAt ?? a?.timestamp ?? 0).getTime();
                const tb = new Date(b?.createdAt ?? b?.timestamp ?? 0).getTime();
                return tb - ta;
            });
            return filtered.slice(0, max);
        }

        const snapshot = await this.db.ref("incidents").get();
        const incidents = snapshot.val() ?? {};
        const rows = Object.values(incidents);
        const filtered = status ? rows.filter((row) => String(row?.status) === String(status)) : rows;
        filtered.sort((a, b) => {
            const ta = new Date(a?.createdAt ?? a?.timestamp ?? 0).getTime();
            const tb = new Date(b?.createdAt ?? b?.timestamp ?? 0).getTime();
            return tb - ta;
        });
        return filtered.slice(0, max);
    }

    async publishChannelMessage(channel, payload) {
        if (this.mode !== "firebase") {
            this.memory.channels[channel] = {
                ...payload,
                updatedAt: nowIso(),
            };
            return;
        }

        await this.db.ref(`channels/${channel}`).set({
            ...payload,
            updatedAt: nowIso(),
        });
    }

    async acquireAvailableDoctor({ hospitalId, crisisType, incidentId }) {
        const doctors = await this.getDoctors();
        const sameHospitalAndSpecialty = doctors
            .filter((doctor) => doctor.hospitalId === hospitalId && matchesSpecialty(doctor.specialty, crisisType))
            .sort((a, b) => String(a.id).localeCompare(String(b.id)));

        const sameHospitalAnySpecialty = doctors
            .filter((doctor) => doctor.hospitalId === hospitalId)
            .sort((a, b) => String(a.id).localeCompare(String(b.id)));

        const globalSpecialty = doctors
            .filter((doctor) => matchesSpecialty(doctor.specialty, crisisType))
            .sort((a, b) => String(a.id).localeCompare(String(b.id)));

        const orderedPool = [...sameHospitalAndSpecialty, ...sameHospitalAnySpecialty, ...globalSpecialty].filter(
            (doctor, index, list) => list.findIndex((candidate) => candidate.id === doctor.id) === index
        );

        for (const candidate of orderedPool) {
            const locked = await this.tryLockDoctor(candidate.id, incidentId);
            if (locked) {
                return locked;
            }
        }

        return null;
    }

    async tryLockDoctor(doctorId, incidentId) {
        if (this.mode !== "firebase") {
            const doctor = this.memory.doctors[doctorId];
            if (!doctor || !isAvailableDoctor(doctor)) {
                return null;
            }
            const updated = {
                ...doctor,
                status: "busy",
                currentstatus: "busy",
                currentIncidentId: incidentId,
                lastUpdated: nowIso(),
            };
            this.memory.doctors[doctorId] = updated;
            return updated;
        }

        const ref = this.db.ref(`doctors/${doctorId}`);
        const result = await ref.transaction((current) => {
            if (!current || !isAvailableDoctor(current)) {
                return current;
            }

            return {
                ...current,
                status: "busy",
                currentstatus: "busy",
                currentIncidentId: incidentId,
                lastUpdated: nowIso(),
            };
        });

        if (!result.committed) {
            return null;
        }

        const value = result.snapshot.val();
        return value?.currentIncidentId === incidentId ? value : null;
    }

    async releaseDoctor({ doctorId, incidentId, currentstatus = "available" }) {
        if (!doctorId) {
            return null;
        }

        if (this.mode !== "firebase") {
            const doctor = this.memory.doctors[doctorId];
            if (!doctor) {
                return null;
            }

            if (doctor.currentIncidentId && doctor.currentIncidentId !== incidentId) {
                return doctor;
            }

            const updated = {
                ...doctor,
                status: "available",
                currentstatus,
                currentIncidentId: null,
                lastUpdated: nowIso(),
            };
            this.memory.doctors[doctorId] = updated;
            return updated;
        }

        const ref = this.db.ref(`doctors/${doctorId}`);
        const result = await ref.transaction((current) => {
            if (!current) {
                return current;
            }

            if (current.currentIncidentId && current.currentIncidentId !== incidentId) {
                return current;
            }

            return {
                ...current,
                status: "available",
                currentstatus,
                currentIncidentId: null,
                lastUpdated: nowIso(),
            };
        });

        return result.snapshot.val() ?? null;
    }

    async dischargeIncident({ incidentId, currentstatus = "discharged", payload = {} }) {
        const incident = await this.getIncidentById(incidentId);
        if (!incident) {
            return null;
        }

        const doctorId = incident?.assignedDoctor?.id ?? incident?.assigned_doctor?.id ?? null;
        await this.releaseDoctor({ doctorId, incidentId, currentstatus: "available" });

        const updatedIncident = {
            ...incident,
            status: "resolved",
            currentstatus,
            discharge: {
                ...payload,
                at: nowIso(),
            },
            updatedAt: nowIso(),
        };

        if (this.mode !== "firebase") {
            this.memory.incidents[incidentId] = updatedIncident;
            return updatedIncident;
        }

        await this.db.ref(`incidents/${incidentId}`).set(updatedIncident);
        return updatedIncident;
    }

    async getDoctors() {
        if (this.mode !== "firebase") {
            return Object.values(this.memory.doctors);
        }

        const snapshot = await this.db.ref("doctors").get();
        const doctors = snapshot.val() ?? {};
        return Object.values(doctors);
    }

    async getRouteCache(cacheKey, maxAgeMinutes = 10) {
        if (this.mode !== "firebase") {
            const row = this.memory.routeCache.get(cacheKey);
            if (!row) {
                return null;
            }
            const ageMs = Date.now() - toMillis(row.updated_at);
            if (ageMs > maxAgeMinutes * 60 * 1000) {
                return null;
            }
            return row;
        }

        const safeKey = makeSafeKey(cacheKey);

        const snapshot = await this.db
        .ref(`routing_cache/${safeKey}`)
        .get();
        if (!snapshot.exists()) {
            return null;
        }

        const row = snapshot.val();
        const ageMs = Date.now() - toMillis(row.updated_at);
        if (ageMs > maxAgeMinutes * 60 * 1000) {
            return null;
        }

        return row;
    }

    async upsertRouteCache(entry) {
        if (this.mode !== "firebase") {
            this.memory.routeCache.set(entry.cache_key, entry);
            return;
        }

        const safeKey = makeSafeKey(entry.cache_key);

        await this.db
        .ref(`routing_cache/${safeKey}`)
        .set({
            ...entry,
            updated_at: nowIso(),
        });
    }
}