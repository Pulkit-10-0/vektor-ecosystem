import { curlExamples, endpointCatalog, testUser } from "../data/testUser.js";
import { runEmergencyPipeline } from "../pipeline/runEmergencyPipeline.js";

const escapeHtml = (value) =>
    String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");

const listMarkup = (items) => items.map((item) => `<li>${escapeHtml(item)}</li>`).join("");

const endpointMarkup = endpointCatalog
    .map(
        (endpoint) => `
            <article class="endpoint-card">
                <div class="endpoint-row">
                    <span class="method">${escapeHtml(endpoint.method)}</span>
                    <span class="path">${escapeHtml(endpoint.path)}</span>
                </div>
                <p>${escapeHtml(endpoint.description)}</p>
            </article>
        `
    )
    .join("");

const curlMarkup = curlExamples
    .map(
        (example) => `
            <article class="curl-card">
                <h3>${escapeHtml(example.title)}</h3>
                <pre><code>${escapeHtml(example.command)}</code></pre>
            </article>
        `
    )
    .join("");

const pageShell = ({ title, body, description = "VectorGo emergency agent engine" }) => `
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="description" content="${escapeHtml(description)}" />
  <title>${escapeHtml(title)}</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f4faf4;
      --panel: #ffffff;
      --panel-soft: #f0f8f0;
      --border: #cbe6cb;
      --text: #10351a;
      --muted: #4a6b51;
      --green: #1f8f4c;
      --green-dark: #126f39;
      --green-soft: #daf3dd;
      --accent: #effbe8;
      --shadow: 0 18px 45px rgba(28, 90, 47, 0.12);
    }

    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: Inter, "Segoe UI", Arial, sans-serif;
      background:
        radial-gradient(circle at top left, rgba(58, 179, 86, 0.15), transparent 30%),
        radial-gradient(circle at top right, rgba(31, 143, 76, 0.12), transparent 24%),
        linear-gradient(180deg, #f8fcf8 0%, #eef8ef 100%);
      color: var(--text);
    }

    .wrap {
      width: min(1180px, calc(100% - 32px));
      margin: 0 auto;
      padding: 28px 0 48px;
    }

    .hero {
      display: grid;
      gap: 20px;
      grid-template-columns: 1.4fr 0.9fr;
      padding: 28px;
      background: linear-gradient(145deg, rgba(255,255,255,0.95), rgba(239, 251, 242, 0.96));
      border: 1px solid var(--border);
      border-radius: 24px;
      box-shadow: var(--shadow);
    }

    .eyebrow {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      width: fit-content;
      padding: 8px 14px;
      border-radius: 999px;
      background: var(--green-soft);
      color: var(--green-dark);
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      font-size: 12px;
    }

    h1, h2, h3, p { margin: 0; }
    h1 { margin-top: 14px; font-size: clamp(2.1rem, 4vw, 4rem); line-height: 1; }
    .lede { margin-top: 14px; font-size: 1.03rem; line-height: 1.6; color: var(--muted); max-width: 64ch; }

    .status-card, .panel {
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 22px;
      box-shadow: var(--shadow);
    }

    .status-card {
      padding: 22px;
      display: grid;
      gap: 12px;
      align-content: start;
    }

    .status-label {
      color: var(--muted);
      font-size: 0.95rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    .status-value {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      font-size: 1.6rem;
      font-weight: 800;
      color: var(--green-dark);
    }

    .pulse {
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: var(--green);
      box-shadow: 0 0 0 0 rgba(31, 143, 76, 0.45);
      animation: pulse 1.8s infinite;
    }

    @keyframes pulse {
      0% { box-shadow: 0 0 0 0 rgba(31, 143, 76, 0.45); }
      70% { box-shadow: 0 0 0 18px rgba(31, 143, 76, 0); }
      100% { box-shadow: 0 0 0 0 rgba(31, 143, 76, 0); }
    }

    .grid {
      margin-top: 22px;
      display: grid;
      gap: 18px;
      grid-template-columns: repeat(12, 1fr);
    }

    .panel { padding: 22px; }
    .span-7 { grid-column: span 7; }
    .span-5 { grid-column: span 5; }
    .span-12 { grid-column: span 12; }
    .span-6 { grid-column: span 6; }

    .section-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 14px; }
    .section-head p { color: var(--muted); margin-top: 6px; line-height: 1.5; }

    .endpoint-grid, .curl-grid, .field-grid {
      display: grid;
      gap: 14px;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    }

    .endpoint-card, .curl-card, .field-card {
      border-radius: 18px;
      border: 1px solid var(--border);
      background: linear-gradient(180deg, #ffffff, #f7fbf7);
      padding: 16px;
    }

    .endpoint-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
    .method {
      background: var(--green);
      color: white;
      border-radius: 999px;
      padding: 4px 10px;
      font-size: 0.78rem;
      font-weight: 800;
      letter-spacing: 0.04em;
    }
    .path {
      font-family: "SFMono-Regular", Consolas, monospace;
      font-size: 0.95rem;
      color: var(--green-dark);
      background: var(--green-soft);
      padding: 4px 8px;
      border-radius: 10px;
    }

    ul { margin: 0; padding-left: 18px; color: var(--muted); line-height: 1.7; }
    .two-col { display: grid; gap: 14px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .small { color: var(--muted); font-size: 0.94rem; line-height: 1.55; }
    pre {
      overflow: auto;
      margin: 12px 0 0;
      padding: 14px;
      border-radius: 16px;
      background: #0f2414;
      color: #ecffef;
      font-size: 0.88rem;
      line-height: 1.65;
    }
    code { font-family: Consolas, Monaco, monospace; }
    .meta-row { display: flex; flex-wrap: wrap; gap: 10px; }
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      border-radius: 999px;
      padding: 8px 12px;
      background: var(--accent);
      color: var(--green-dark);
      border: 1px solid var(--border);
      font-weight: 700;
      font-size: 0.92rem;
    }

    @media (max-width: 900px) {
      .hero, .two-col { grid-template-columns: 1fr; }
      .span-7, .span-5, .span-6, .span-12 { grid-column: span 12; }
    }
  </style>
</head>
<body>
  <main class="wrap">
    ${body}
  </main>
</body>
</html>
`;

const landingPage = () =>
    pageShell({
        title: "VectorGo Agent Engine",
        body: `
          <section class="hero">
            <div>
              <div class="eyebrow">Emergency intelligence · on-device first</div>
              <h1>if you are seeing this page, the agent is still running.</h1>
              <p class="lede">
                This is the public web surface for the service, with the
                current status, endpoint catalog, and curl/Postman examples for quick checks.
              </p>
              <div class="meta-row" style="margin-top: 18px;">
                <span class="pill">Single page service</span>
                <span class="pill">Render-ready container</span>
                <span class="pill">Test user UID 1</span>
                <span class="pill">QR profile available</span>
              </div>
            </div>
            <aside class="status-card">
              <span class="status-label">Status</span>
              <div class="status-value"><span class="pulse"></span> running</div>
              <div class="small">Service name: vectorgo-agent-engine</div>
              <div class="small">Default deployment target: Render</div>
              <div class="small">Open <a href="/qr/1">/qr/1</a> for the hardcoded patient profile.</div>
            </aside>
          </section>

          <section class="grid">
            <article class="panel span-7">
              <div class="section-head">
                <div>
                  <h2>Endpoint list</h2>
                  <p>These are the endpoints you can test with curl or Postman.</p>
                </div>
              </div>
              <div class="endpoint-grid">${endpointMarkup}</div>
            </article>

            <article class="panel span-5">
              <div class="section-head">
                <div>
                  <h2>Hardcoded test user</h2>
                  <p>The same UID is used for the QR page and the demo login flow.</p>
                </div>
              </div>
              <div class="field-grid">
                <div class="field-card"><strong>UID</strong><div class="small">${escapeHtml(testUser.uid)}</div></div>
                <div class="field-card"><strong>Username</strong><div class="small">${escapeHtml(testUser.username)}</div></div>
                <div class="field-card"><strong>Password</strong><div class="small">${escapeHtml(testUser.password)}</div></div>
                <div class="field-card"><strong>Name</strong><div class="small">${escapeHtml(testUser.name)}</div></div>
              </div>
              <p class="small" style="margin-top: 14px;">The QR profile page includes name, date of birth, blood group, allergies, conditions, medications, emergency contact, medical history, insurance, and the QR path.</p>
            </article>

            <article class="panel span-12">
              <div class="section-head">
                <div>
                  <h2>curl / Postman examples</h2>
                  <p>Use these directly against the running Render URL or your local container.</p>
                </div>
              </div>
              <div class="curl-grid">${curlMarkup}</div>
            </article>

            <article class="panel span-12">
              <div class="section-head">
                <div>
                  <h2>What is deployed</h2>
                  <p>A Node service with a , health endpoints, emergency endpoints, and a patient QR page.</p>
                </div>
              </div>
              <div class="two-col">
                <div class="field-card">
                  <strong>Deployment notes</strong>
                  <ul>
                    <li>Runs on the PORT environment variable.</li>
                    <li>Falls back to in-memory storage when no database URL is provided.</li>
                    <li>Uses a single external web surface with JSON APIs for testing.</li>
                  </ul>
                </div>
                <div class="field-card">
                  <strong>Primary URL behavior</strong>
                  <ul>
                    <li><a href="/">/</a> shows the running status page.</li>
                    <li><a href="/qr/1">/qr/1</a> shows the patient profile page.</li>
                    <li><a href="/ready">/ready</a> shows service readiness.</li>
                  </ul>
                </div>
              </div>
            </article>
          </section>
        `,
    });

const profilePage = (uid) =>
    pageShell({
        title: `VectorGo QR · ${testUser.name}`,
        description: `Emergency QR profile for ${testUser.name}`,
        body: `
          <section class="hero" style="grid-template-columns: 1fr;">
            <div>
              <div class="eyebrow">QR profile</div>
              <h1>${escapeHtml(testUser.name)}</h1>
              <p class="lede">UID ${escapeHtml(uid)} · emergency responder view with the full patient summary required by the PRD.</p>
            </div>
          </section>

          <section class="grid">
            <article class="panel span-12">
              <div class="section-head">
                <div>
                  <h2>Patient overview</h2>
                  <p>This page is intentionally simple so it can load fast on any browser without an app.</p>
                </div>
              </div>
              <div class="two-col">
                <div class="field-card"><strong>Name</strong><div class="small">${escapeHtml(testUser.name)}</div></div>
                <div class="field-card"><strong>Date of birth</strong><div class="small">${escapeHtml(testUser.dob)}</div></div>
                <div class="field-card"><strong>Blood group</strong><div class="small">${escapeHtml(testUser.bloodGroup)}</div></div>
                <div class="field-card"><strong>Address</strong><div class="small">${escapeHtml(testUser.address)}</div></div>
              </div>
            </article>

            <article class="panel span-6">
              <h2>Allergies and conditions</h2>
              <p class="small" style="margin-top: 8px;">Critical information for first responders.</p>
              <div class="two-col" style="margin-top: 14px;">
                <div class="field-card">
                  <strong>Allergies</strong>
                  <ul>${listMarkup(testUser.allergies)}</ul>
                </div>
                <div class="field-card">
                  <strong>Conditions</strong>
                  <ul>${listMarkup(testUser.conditions)}</ul>
                </div>
              </div>
            </article>

            <article class="panel span-6">
              <h2>Medication and history</h2>
              <div class="field-card" style="margin-top: 14px;">
                <strong>Medications</strong>
                <ul>${listMarkup(testUser.medications)}</ul>
              </div>
              <div class="field-card" style="margin-top: 14px;">
                <strong>Medical history</strong>
                <p class="small" style="margin-top: 8px;">${escapeHtml(testUser.medicalHistory)}</p>
              </div>
            </article>

            <article class="panel span-12">
              <h2>Emergency contact and insurance</h2>
              <div class="two-col" style="margin-top: 14px;">
                <div class="field-card">
                  <strong>Emergency contact</strong>
                  <div class="small" style="margin-top: 8px;">
                    ${escapeHtml(testUser.emergencyContacts[0].name)}<br />
                    ${escapeHtml(testUser.emergencyContacts[0].relation)}<br />
                    ${escapeHtml(testUser.emergencyContacts[0].phone)}
                  </div>
                </div>
                <div class="field-card">
                  <strong>Insurance</strong>
                  <div class="small" style="margin-top: 8px;">
                    Provider: ${escapeHtml(testUser.insuranceProvider)}<br />
                    Policy No: ${escapeHtml(testUser.insurancePolicyNo)}<br />
                    QR path: <a href="${escapeHtml(testUser.qrPath)}">${escapeHtml(testUser.qrPath)}</a>
                  </div>
                </div>
              </div>
            </article>
          </section>
        `,
    });

const jsonProfile = () => ({
    ok: true,
    uid: testUser.uid,
    profile: testUser,
});

export const publicRoutes = async (fastify) => {
    fastify.get("/", async (_request, reply) => {
        return reply.type("text/html; charset=utf-8").send(landingPage());
    });

    fastify.post(
        "/emergency",
        {
            schema: {
                body: {
                    type: "object",
                    additionalProperties: true,
                    properties: {
                        source: { type: "string" },
                        timestamp: { type: "string" },
                        idempotency_key: { type: "string" },
                        location: {
                            type: "object",
                            properties: {
                                lat: { type: "number" },
                                lng: { type: "number" },
                            },
                            required: ["lat", "lng"],
                        },
                        patient: {
                            type: "object",
                            properties: {
                                spo2: { type: "number" },
                                heart_rate: { type: "number" },
                                symptoms: { type: "array", items: { type: "string" } },
                            },
                            required: ["spo2", "heart_rate"],
                        },
                    },
                    required: ["location", "patient"],
                },
            },
        },
        async (request, reply) => {
            const result = await runEmergencyPipeline({
                payload: request.body,
                repository: fastify.repository,
                aiClient: fastify.aiClient,
                routingClient: fastify.routingClient,
                realtime: fastify.realtime,
                logger: request.log,
            });

            return reply.code(200).send(result);
        }
    );

    fastify.get("/qr/:uid", async (request, reply) => {
        const { uid } = request.params;

        if (uid !== testUser.uid) {
            return reply.code(404).type("text/html; charset=utf-8").send(
                pageShell({
                    title: "VectorGo QR not found",
                    body: `
                      <section class="hero" style="grid-template-columns: 1fr;">
                        <div>
                          <div class="eyebrow">QR profile not found</div>
                          <h1>Unknown UID</h1>
                          <p class="lede">This demo deployment only includes the hardcoded test user with UID 1.</p>
                          <p class="lede"><a href="/">Return to the status page</a>.</p>
                        </div>
                      </section>
                    `,
                })
            );
        }

        return reply.type("text/html; charset=utf-8").send(profilePage(uid));
    });

    fastify.post("/check-uid", async (request, reply) => {
        const requestedUid = request.body?.uid ?? testUser.uid;

        if (requestedUid !== testUser.uid) {
            return reply.code(404).send({ ok: false, message: "Unknown UID", requested_uid: requestedUid });
        }

        return reply.send(jsonProfile());
    });

    fastify.post("/bystander-report", async (request, reply) => {
        return reply.send({
            ok: true,
            route: "bystander-report",
            mode: "guest",
            received: request.body ?? null,
            patient: testUser,
        });
    });

    fastify.get("/sync/:uid", async (request, reply) => {
        const { uid } = request.params;

        if (uid !== testUser.uid) {
            return reply.code(404).send({ ok: false, message: "Unknown UID", requested_uid: uid });
        }

        return reply.send({
            ok: true,
            uid,
            synced: true,
            queued_items: 0,
            patient: testUser,
        });
    });
};