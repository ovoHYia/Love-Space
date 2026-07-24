ALTER TABLE memories ADD COLUMN latitude DECIMAL(9, 6) NULL;
ALTER TABLE memories ADD COLUMN longitude DECIMAL(9, 6) NULL;
CREATE INDEX idx_memories_map ON memories(couple_id, latitude, longitude);

CREATE TABLE memory_tags (
    memory_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL,
    CONSTRAINT pk_memory_tags PRIMARY KEY (memory_id, tag),
    CONSTRAINT fk_memory_tags_memory FOREIGN KEY (memory_id) REFERENCES memories(id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_tags_tag ON memory_tags(tag);
