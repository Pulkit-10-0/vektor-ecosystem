import admin from "firebase-admin";
import { env } from "../config/env.js";
import { doctorSeed } from "../data/doctors.js";
import { nowIso } from "../lib/time.js";

function requireFirebaseEnv() {
    const missing = [];
    if (!env.firebaseDatabaseUrl) missing.push("FIREBASE_DATABASE_URL");
    if (!env.firebaseProjectId) missing.push("FIREBASE_PROJECT_ID");
    if (!env.firebaseClientEmail) missing.push("FIREBASE_CLIENT_EMAIL");
    if (!env.firebasePrivateKey) missing.push("FIREBASE_PRIVATE_KEY");
    return missing;
}

async function main() {
    const missing = requireFirebaseEnv();
    if (missing.length > 0) {
        console.error(`[seedDoctors] Missing Firebase env vars: ${missing.join(", ")}`);
        process.exitCode = 1;
        return;
    }

    if (admin.apps.length === 0) {
        admin.initializeApp({
            credential: admin.credential.cert({
                projectId: env.firebaseProjectId,
                clientEmail: env.firebaseClientEmail,
                privateKey: env.firebasePrivateKey.replace(/\\n/g, "\n"),
            }),
            databaseURL: env.firebaseDatabaseUrl,
        });
    }

    const db = admin.database();
    const doctorsRef = db.ref("doctors");

    const snap = await doctorsRef.get();
    const existing = snap.val();

    if (existing && Object.keys(existing).length > 0) {
        console.log("[seedDoctors] /doctors is not empty; skipping seed.");
        return;
    }

    const payload = Object.fromEntries(
        doctorSeed.map((doctor) => [
            doctor.id,
            {
                ...doctor,
                lastUpdated: nowIso(),
            },
        ])
    );

    await doctorsRef.set(payload);

    for (const doctor of Object.values(payload)) {
        console.log(`[seedDoctors] wrote ${doctor.id} (${doctor.name}) -> ${doctor.hospitalId}`);
    }

    console.log(`[seedDoctors] done (${Object.keys(payload).length} doctors)`);
}

main().catch((err) => {
    console.error("[seedDoctors] failed", err);
    process.exitCode = 1;
});

