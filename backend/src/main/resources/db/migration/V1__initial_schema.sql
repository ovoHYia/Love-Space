CREATE TABLE couples (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_name VARCHAR(100) NOT NULL,
    love_started_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar_media_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT fk_users_couple FOREIGN KEY (couple_id) REFERENCES couples(id)
);

CREATE TABLE moods (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    mood_date DATE NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    label VARCHAR(30) NOT NULL,
    note VARCHAR(300) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_moods_user_date UNIQUE (user_id, mood_date),
    CONSTRAINT fk_moods_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_moods_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE memories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NULL,
    event_at TIMESTAMP NOT NULL,
    location VARCHAR(200) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_memories_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_memories_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE media (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    memory_id BIGINT NULL,
    stored_name VARCHAR(100) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_media_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_media_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_media_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_media_memory FOREIGN KEY (memory_id) REFERENCES memories(id) ON DELETE CASCADE
);

ALTER TABLE users ADD CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_media_id) REFERENCES media(id);

CREATE TABLE diaries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    diary_date DATE NOT NULL,
    mood VARCHAR(30) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diaries_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_diaries_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_messages_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);

CREATE TABLE anniversaries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    event_date DATE NOT NULL,
    type VARCHAR(30) NOT NULL,
    recurring_yearly BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_days INT NOT NULL DEFAULT 0,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anniversaries_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_anniversaries_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_users_couple ON users(couple_id);
CREATE INDEX idx_memories_couple_event ON memories(couple_id, event_at);
CREATE INDEX idx_media_couple ON media(couple_id);
CREATE INDEX idx_diaries_couple_date ON diaries(couple_id, diary_date);
CREATE INDEX idx_messages_couple_created ON messages(couple_id, created_at);
CREATE INDEX idx_anniversaries_couple_date ON anniversaries(couple_id, event_date);
