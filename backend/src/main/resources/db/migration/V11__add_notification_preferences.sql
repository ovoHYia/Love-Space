CREATE TABLE notification_preferences (
    user_id BIGINT NOT NULL PRIMARY KEY,
    couple_id BIGINT NOT NULL,
    anniversary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    letter_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    wish_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notification_preferences_couple FOREIGN KEY (couple_id) REFERENCES couples(id)
);

CREATE INDEX idx_notification_preferences_couple ON notification_preferences(couple_id);
