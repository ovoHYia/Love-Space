CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(500) NOT NULL,
    reference_type VARCHAR(30) NULL,
    reference_id BIGINT NULL,
    dedupe_key VARCHAR(150) NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notifications_user_dedupe UNIQUE (user_id, dedupe_key),
    CONSTRAINT fk_notifications_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at);
