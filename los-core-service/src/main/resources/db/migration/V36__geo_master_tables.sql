-- India geography master (states + cities); seeded in V37.

CREATE TABLE state_master (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code   VARCHAR(20)  NOT NULL UNIQUE,
    state_name   VARCHAR(120) NOT NULL UNIQUE,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_state_master_state_name ON state_master(state_name);
CREATE INDEX idx_state_master_is_active ON state_master(is_active);

CREATE TABLE city_master (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id    UUID NOT NULL REFERENCES state_master(id) ON DELETE CASCADE,
    city_name   VARCHAR(200) NOT NULL,
    city_code   VARCHAR(20),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_city_master_state_city UNIQUE (state_id, city_name)
);

CREATE INDEX idx_city_master_state_id ON city_master(state_id);
CREATE INDEX idx_city_master_city_name ON city_master(city_name);
CREATE INDEX idx_city_master_is_active ON city_master(is_active);
