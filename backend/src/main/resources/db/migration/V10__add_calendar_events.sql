CREATE TABLE calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(30) NOT NULL,
    location VARCHAR(200) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by BIGINT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_calendar_events_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_calendar_events_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_calendar_events_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id)
);

CREATE INDEX idx_calendar_events_range
    ON calendar_events(couple_id, deleted_at, start_at, end_at);
CREATE INDEX idx_calendar_events_trash
    ON calendar_events(couple_id, deleted_by, deleted_at);
