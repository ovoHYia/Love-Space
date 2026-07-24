CREATE TABLE wishes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    category VARCHAR(30) NOT NULL,
    target_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    completed_by BIGINT NULL,
    completed_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wishes_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_wishes_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_wishes_completed_by FOREIGN KEY (completed_by) REFERENCES users(id)
);

CREATE INDEX idx_wishes_couple_status_target
    ON wishes(couple_id, status, target_date);
