ALTER TABLE memories ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE memories ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE memories ADD CONSTRAINT fk_memories_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);
CREATE INDEX idx_memories_trash ON memories(couple_id, deleted_by, deleted_at);

ALTER TABLE diaries ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE diaries ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE diaries ADD CONSTRAINT fk_diaries_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);
CREATE INDEX idx_diaries_trash ON diaries(couple_id, deleted_by, deleted_at);

ALTER TABLE messages ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE messages ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE messages ADD CONSTRAINT fk_messages_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);
CREATE INDEX idx_messages_trash ON messages(couple_id, deleted_by, deleted_at);

ALTER TABLE anniversaries ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE anniversaries ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE anniversaries ADD CONSTRAINT fk_anniversaries_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);
CREATE INDEX idx_anniversaries_trash ON anniversaries(couple_id, deleted_by, deleted_at);

ALTER TABLE wishes ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE wishes ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE wishes ADD CONSTRAINT fk_wishes_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);
CREATE INDEX idx_wishes_trash ON wishes(couple_id, deleted_by, deleted_at);
