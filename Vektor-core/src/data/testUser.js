export const testUser = {
    uid: "1",
    username: "admin",
    password: "1234",
    name: "Arjun Mehta",
    dob: "1998-04-12",
    bloodGroup: "O+",
    allergies: ["Penicillin", "Latex"],
    conditions: ["Type 2 Diabetes"],
    medications: ["Metformin 500mg twice daily"],
    emergencyContacts: [
        {
            name: "Priya Mehta",
            phone: "+91-9876543210",
            relation: "Sister",
        },
    ],
    medicalHistory:
        "Patient has well-controlled Type 2 Diabetes diagnosed in 2020. No cardiac history. Penicillin allergy confirmed (anaphylaxis risk). Latex allergy (contact dermatitis). Last HbA1c: 6.8% (March 2026).",
    insuranceProvider: "Star Health",
    insurancePolicyNo: "SH-2024-XXXX",
    address: "Bengaluru, Karnataka, India",
    qrPath: "/qr/1",
};

export const endpointCatalog = [
    {
        method: "GET",
        path: "/",
        description: "Landing page showing the running status, deployment notes, and endpoint catalog.",
    },
    {
        method: "GET",
        path: "/health",
        description: "Liveness check for the service.",
    },
    {
        method: "GET",
        path: "/ready",
        description: "Readiness check, including database/integration status.",
    },
    {
        method: "POST",
        path: "/check-uid",
        description: "Registers or validates the hardcoded test UID and returns the patient profile.",
    },
    {
        method: "POST",
        path: "/emergency",
        description: "Primary emergency submission endpoint for the VectorGo pipeline.",
    },
    {
        method: "POST",
        path: "/bystander-report",
        description: "Guest-mode emergency report generated from the lock screen or QR page.",
    },
    {
        method: "GET",
        path: "/sync/:uid",
        description: "Reconciles offline data for a given UID.",
    },
    {
        method: "GET",
        path: "/qr/:uid",
        description: "Responder page with the emergency profile for that UID.",
    },
    {
        method: "POST",
        path: "/api/emergency",
        description: "Legacy alias for the emergency pipeline.",
    },
    {
        method: "POST",
        path: "/api/offline-sync",
        description: "Legacy alias for offline emergency reconciliation.",
    },
];

export const curlExamples = [
    {
        title: "Check the running page",
        command: "curl http://localhost:3000/",
    },
    {
        title: "Validate UID 1",
        command: "curl -X POST http://localhost:3000/check-uid -H \"Content-Type: application/json\" -d '{\"uid\":\"1\"}'",
    },
    {
        title: "Trigger emergency pipeline",
        command:
            "curl -X POST http://localhost:3000/emergency -H \"Content-Type: application/json\" -d '{\"location\":{\"lat\":12.97,\"lng\":77.59},\"patient\":{\"spo2\":87,\"heart_rate\":138,\"symptoms\":[\"breathlessness\"]}}'",
    },
    {
        title: "Open the QR profile",
        command: "curl http://localhost:3000/qr/1",
    },
];