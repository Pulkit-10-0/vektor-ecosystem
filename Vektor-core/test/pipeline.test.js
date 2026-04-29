import test from "node:test";


import assert from "node:assert/strict";
import { buildApp } from "../src/app.js";

const samplePayload = {
    source: "iot",
    timestamp: new Date().toISOString(),
    location: { lat: 12.97, lng: 77.59 },
    patient: {
        spo2: 87,
        heart_rate: 138,
        symptoms: ["breathlessness"],
    },
};

test("POST /api/emergency runs full pipeline", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "POST",
        url: "/api/emergency",
        payload: samplePayload,
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.classification.severity, "critical");
    assert.equal(body.decision.needs_icu, true);
    assert.ok(body.hospital.hospital_id);
    assert.ok(body.routing.eta >= 1);
});

test("POST /emergency aliases the emergency pipeline", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "POST",
        url: "/emergency",
        payload: samplePayload,
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.classification.severity, "critical");
    assert.equal(body.decision.needs_icu, true);
});

test("POST /api/offline-sync returns feedback", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "POST",
        url: "/api/offline-sync",
        payload: {
            payload: samplePayload,
            offline_decision: {
                decision: { priority: "normal" },
                hospital: { hospital_id: "H003" },
            },
        },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.synced, true);
    assert.equal(typeof body.feedback.override_required, "boolean");
});

test("GET /ready reports service status", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "GET",
        url: "/ready",
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.status, "ready");
    assert.equal(body.service, "vectorgo-agent-engine");
    assert.equal(typeof body.database.ok, "boolean");
});

test("GET / renders the running landing page", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "GET",
        url: "/",
    });

    assert.equal(response.statusCode, 200);
    assert.match(response.headers["content-type"], /text\/html/);
    assert.match(response.body, /the agent is still running/i);
    assert.match(response.body, /Status/i);
    assert.match(response.body, /Endpoint list/i);
});

test("GET /qr/1 renders the test user profile", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "GET",
        url: "/qr/1",
    });

    assert.equal(response.statusCode, 200);
    assert.match(response.headers["content-type"], /text\/html/);
    assert.match(response.body, /Arjun Mehta/);
    assert.match(response.body, /1998-04-12/);
    assert.match(response.body, /Penicillin/);
    assert.match(response.body, /Metformin 500mg twice daily/);
});

test("POST /check-uid returns the hardcoded profile", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "POST",
        url: "/check-uid",
        payload: { uid: "1" },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.ok, true);
    assert.equal(body.profile.uid, "1");
    assert.equal(body.profile.name, "Arjun Mehta");
});

test("GET /sync/1 returns the queued sync payload", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "GET",
        url: "/sync/1",
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.ok, true);
    assert.equal(body.synced, true);
    assert.equal(body.uid, "1");
});

test("POST /api/emergency validates payload shape", async (t) => {
    const app = await buildApp();
    t.after(async () => {
        await app.close();
    });

    const response = await app.inject({
        method: "POST",
        url: "/api/emergency",
        payload: {
            patient: {
                spo2: 87,
                heart_rate: 138,
            },
        },
    });

    assert.equal(response.statusCode, 400);
    const body = response.json();
    assert.equal(body.error, "validation_error");
});
