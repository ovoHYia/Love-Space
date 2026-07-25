CREATE TABLE game_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    game_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    current_turn_user_id BIGINT NULL,
    round_number INT NOT NULL DEFAULT 1,
    state_json LONGTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT pk_game_sessions PRIMARY KEY (id),
    CONSTRAINT fk_game_sessions_couple FOREIGN KEY (couple_id) REFERENCES couples(id),
    CONSTRAINT fk_game_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_game_sessions_turn_user FOREIGN KEY (current_turn_user_id) REFERENCES users(id)
);

CREATE INDEX idx_game_sessions_couple_updated ON game_sessions(couple_id, updated_at);
CREATE INDEX idx_game_sessions_active ON game_sessions(couple_id, game_type, status);
