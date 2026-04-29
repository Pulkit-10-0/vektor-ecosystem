const scenarios = [
    {
        label: "Hotel Alpha — Cardiac",
        location: { lat: 28.6139, lng: 77.209 },
        patient: {
            id: "PAT-001",
            name: "Ravi Sharma",
            age: 54,
            spo2: 87,
            heart_rate: 138,
            symptoms: ["chest_pain", "breathlessness"],
        },
    },
    {
        label: "Hotel Beta — Trauma",
        location: { lat: 28.62, lng: 77.215 },
        patient: {
            id: "PAT-002",
            name: "Neha Verma",
            age: 31,
            spo2: 94,
            heart_rate: 115,
            symptoms: ["trauma", "bleeding"],
        },
    },
    {
        label: "Hospital Lobby — Respiratory",
        location: { lat: 28.605, lng: 77.198 },
        patient: {
            id: "PAT-003",
            name: "Arjun Mehta",
            age: 67,
            spo2: 85,
            heart_rate: 122,
            symptoms: ["breathlessness", "low_spo2"],
        },
    },
    {
        label: "Office Tower — Cardiac Critical",
        location: { lat: 28.63, lng: 77.22 },
        patient: {
            id: "PAT-004",
            name: "Priya Nair",
            age: 45,
            spo2: 89,
            heart_rate: 142,
            symptoms: ["chest_pain", "dizziness"],
        },
    },
    {
        label: "Mall Floor 2 — General",
        location: { lat: 28.598, lng: 77.21 },
        patient: {
            id: "PAT-005",
            name: "Karan Singh",
            age: 28,
            spo2: 96,
            heart_rate: 108,
            symptoms: ["dizziness", "nausea"],
        },
    },
    {
        label: "Airport Terminal — Seizure",
        location: { lat: 28.5562, lng: 77.1 },
        patient: {
            id: "PAT-006",
            name: "Meera Iyer",
            age: 38,
            spo2: 91,
            heart_rate: 130,
            symptoms: ["seizure", "unconscious"],
        },
    },
    {
        label: "Hotel Gamma — Trauma Critical",
        location: { lat: 28.64, lng: 77.23 },
        patient: {
            id: "PAT-007",
            name: "Rahul Das",
            age: 42,
            spo2: 86,
            heart_rate: 145,
            symptoms: ["trauma", "chest_pain", "bleeding"],
        },
    },
    {
        label: "Stadium — Respiratory",
        location: { lat: 28.635, lng: 77.245 },
        patient: {
            id: "PAT-008",
            name: "Sunita Rao",
            age: 61,
            spo2: 88,
            heart_rate: 125,
            symptoms: ["breathlessness", "chest_pain"],
        },
    },
];

let currentIndex = 0;

const port = process.env.PORT ?? "3002";
const baseUrl = process.env.SIMULATOR_BASE_URL ?? `http://127.0.0.1:${port}`;
const emergencyUrl = `${baseUrl}/api/emergency`;

async function triggerNextScenario() {
    const scenario = scenarios[currentIndex % scenarios.length];
    currentIndex++;

    console.log(`\n[SIMULATOR] Triggering: ${scenario.label}`);
    console.log(`[SIMULATOR] Patient: ${scenario.patient.name}`);
    console.log(`[SIMULATOR] Location: ${scenario.location.lat}, ${scenario.location.lng}`);

    try {
        const response = await fetch(emergencyUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                location: scenario.location,
                patient: scenario.patient,
            }),
        });

        if (!response.ok) {
            const text = await response.text();
            console.log(`[SIMULATOR] Backend responded ${response.status}: ${text.slice(0, 400)}`);
            console.log("----------------------------------------------------");
            return;
        }

        const result = await response.json();

        console.log(`[SIMULATOR] Pipeline complete in ${result.timing_ms?.total}ms`);
        console.log(`[SIMULATOR] Severity: ${result.classification?.severity}`);
        console.log(`[SIMULATOR] Hospital: ${result.hospital?.hospital_name}`);
        console.log(`[SIMULATOR] ETA: ${result.hospital?.eta} mins`);
        console.log(`[SIMULATOR] Doctor: ${result.assignedDoctor?.name || "assigned"}`);
        console.log(`[SIMULATOR] Pusher broadcast sent to admin-dashboard channel`);
        console.log("----------------------------------------------------");
    } catch (err) {
        console.log(`[SIMULATOR] Failed to reach backend at ${emergencyUrl}`);
        console.log(`[SIMULATOR] ${err?.cause?.code ?? err?.code ?? err?.message ?? String(err)}`);
        console.log("----------------------------------------------------");
    }
}

function getRandomDelay() {
    return Math.floor(Math.random() * 1000) + 5000;
}

function startSimulator() {
    console.log("╔════════════════════════════════════════╗");
    console.log("║   RCR DEMO SIMULATOR STARTED           ║");
    console.log("║   Triggering emergency every 5-6 secs  ║");
    console.log("║   Press Ctrl+C to stop                 ║");
    console.log("╚════════════════════════════════════════╝\n");

    triggerNextScenario();

    function loop() {
        const delay = getRandomDelay();
        console.log(`[SIMULATOR] Next trigger in ${delay}ms...`);
        setTimeout(() => {
            triggerNextScenario();
            loop();
        }, delay);
    }

    loop();
}

startSimulator();
