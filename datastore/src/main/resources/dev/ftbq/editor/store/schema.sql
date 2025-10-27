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

CREATE INDEX IF NOT EXISTS idx_items_display_name ON items(display_name);
CREATE INDEX IF NOT EXISTS idx_items_mod_id ON items(mod_id);
CREATE INDEX IF NOT EXISTS idx_items_version ON items(version);

CREATE TABLE IF NOT EXISTS loot_tables (
    name TEXT PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS quests (
    id TEXT PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS quests (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    icon TEXT NOT NULL,
    icon_relative_path TEXT,
    visibility TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS quest_tasks (
    quest_id TEXT NOT NULL,
    task_index INTEGER NOT NULL,
    type TEXT NOT NULL,
    item_id TEXT,
    item_count INTEGER,
    consume INTEGER,
    advancement_id TEXT,
    dimension TEXT,
    x REAL,
    y REAL,
    z REAL,
    radius REAL,
    PRIMARY KEY (quest_id, task_index),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quest_rewards (
    quest_id TEXT NOT NULL,
    reward_index INTEGER NOT NULL,
    type TEXT NOT NULL,
    item_id TEXT,
    item_count INTEGER,
    amount INTEGER,
    command TEXT,
    as_player INTEGER,
    metadata TEXT,
    PRIMARY KEY (quest_id, reward_index),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quest_dependencies (
    quest_id TEXT NOT NULL,
    dependency_quest_id TEXT NOT NULL,
    required INTEGER NOT NULL,
    PRIMARY KEY (quest_id, dependency_quest_id),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quest_positions (
    quest_id TEXT PRIMARY KEY,
    x REAL NOT NULL,
    y REAL NOT NULL,
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);
