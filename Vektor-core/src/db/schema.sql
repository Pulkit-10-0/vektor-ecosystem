CREATE TABLE IF NOT EXISTS emergency_events (
  id TEXT PRIMARY KEY,
  idempotency_key TEXT UNIQUE NOT NULL,
  source TEXT NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL,
  normalized JSONB NOT NULL,
  classification JSONB NOT NULL,
  decision JSONB NOT NULL,
  hospital_selection JSONB NOT NULL,
  routing JSONB NOT NULL,
  final_decision JSONB NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hospitals (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  has_icu BOOLEAN NOT NULL,
  specialties TEXT[] NOT NULL,
  capacity_score DOUBLE PRECISION NOT NULL,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS routing_cache (
  cache_key TEXT PRIMARY KEY,
  distance_km DOUBLE PRECISION NOT NULL,
  eta_minutes INTEGER NOT NULL,
  geometry JSONB,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
