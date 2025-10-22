CREATE TABLE IF NOT EXISTS items (
    id TEXT PRIMARY KEY,
    display_name TEXT,
    is_vanilla INTEGER,
    mod_id TEXT,
    mod_name TEXT,
    tags TEXT,
    texture_path TEXT,
    icon_hash TEXT,
    source_jar TEXT,
    version TEXT,
    kind TEXT
);

CREATE TABLE IF NOT EXISTS loot_tables (
    name TEXT PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT
);
