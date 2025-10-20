CREATE TABLE IF NOT EXISTS generated_usernames (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    language VARCHAR(10) NOT NULL,
    pattern_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username)
);
