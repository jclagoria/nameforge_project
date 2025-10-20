-- ============================================
-- NameForge Database Schema
-- Initial database schema for username generation system
-- ============================================

-- Create generated_usernames table
CREATE TABLE IF NOT EXISTS generated_usernames (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    language        VARCHAR(10) NOT NULL,
    pattern_type    VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_used         BOOLEAN NOT NULL DEFAULT FALSE,
    used_at         TIMESTAMP NULL
);

-- Create indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_username_lookup ON generated_usernames(username);
CREATE INDEX IF NOT EXISTS idx_language_lookup ON generated_usernames(language);
CREATE INDEX IF NOT EXISTS idx_unused_usernames ON generated_usernames(is_used) WHERE is_used = FALSE;
CREATE INDEX IF NOT EXISTS idx_created_at ON generated_usernames(created_at DESC);

-- Add comments for documentation
COMMENT ON TABLE generated_usernames IS 'Stores all generated usernames with metadata';
COMMENT ON COLUMN generated_usernames.id IS 'Primary key, auto-incremented';
COMMENT ON COLUMN generated_usernames.username IS 'Generated username value (unique)';
COMMENT ON COLUMN generated_usernames.language IS 'Language code (EN, ES, FR, etc.)';
COMMENT ON COLUMN generated_usernames.pattern_type IS 'Pattern type used for generation';
COMMENT ON COLUMN generated_usernames.created_at IS 'Timestamp when username was generated';
COMMENT ON COLUMN generated_usernames.is_used IS 'Flag indicating if username has been assigned to a user';
COMMENT ON COLUMN generated_usernames.used_at IS 'Timestamp when username was marked as used';
