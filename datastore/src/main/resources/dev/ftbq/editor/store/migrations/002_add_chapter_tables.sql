CREATE TABLE IF NOT EXISTS chapters(
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  icon TEXT NOT NULL,
  icon_relative_path TEXT,
  background_texture TEXT NOT NULL,
  background_relative_path TEXT,
  background_path TEXT,
  background_color_hex TEXT,
  background_alignment TEXT,
  background_repeat TEXT,
  visibility TEXT NOT NULL,
  ord INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS chapter_quests(
  chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  quest_id TEXT NOT NULL REFERENCES quest_details(id) ON DELETE CASCADE,
  ord INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (chapter_id, quest_id)
);

CREATE INDEX IF NOT EXISTS idx_chapter_quests_quest ON chapter_quests(quest_id);
