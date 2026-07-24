ALTER TABLE messages ADD COLUMN scheduled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN deliver_at DATETIME NULL;
ALTER TABLE messages ADD COLUMN notified_at DATETIME NULL;

UPDATE messages
SET deliver_at = created_at,
    notified_at = created_at
WHERE deliver_at IS NULL;

ALTER TABLE messages MODIFY deliver_at DATETIME NOT NULL;

CREATE INDEX idx_messages_pending_delivery
    ON messages(scheduled, notified_at, deliver_at);
