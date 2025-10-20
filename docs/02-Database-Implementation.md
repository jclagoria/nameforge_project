# NameForge Backend - Implementación de Base de Datos

## 📋 Información del Documento

**Proyecto**: NameForge - Database Layer
**Tecnología**: PostgreSQL 15+ con R2DBC
**Paradigma**: Reactive Database Access
**Fecha**: 2024-09-24

## 🗄️ Diseño de Schema

### Tablas Principales

#### 1. generated_usernames
```sql
CREATE TABLE generated_usernames (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    language VARCHAR(5) NOT NULL CHECK (language IN ('EN', 'ES')),
    pattern_type VARCHAR(20) NOT NULL CHECK (pattern_type IN ('CLASSIC', 'SEPARATOR', 'WORDPLAY')),
    category VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,

    -- Constraints
    CONSTRAINT chk_username_format CHECK (
        username ~ '^[a-z0-9_-]{5,30}$'
    ),
    CONSTRAINT chk_username_length CHECK (
        char_length(username) BETWEEN 5 AND 30
    )
);

-- Comentarios para documentación
COMMENT ON TABLE generated_usernames IS 'Almacena todos los usernames generados y su metadata';
COMMENT ON COLUMN generated_usernames.username IS 'Username único generado, formato: [a-z0-9_-]{5,30}';
COMMENT ON COLUMN generated_usernames.pattern_type IS 'Algoritmo usado: CLASSIC, SEPARATOR, WORDPLAY';
COMMENT ON COLUMN generated_usernames.is_used IS 'Indica si el username ya fue asignado a un usuario';
```

#### 2. safe_words_cache
```sql
CREATE TABLE safe_words_cache (
    word VARCHAR(50) PRIMARY KEY,
    language VARCHAR(5) NOT NULL CHECK (language IN ('EN', 'ES')),
    category VARCHAR(30) NOT NULL,
    is_safe BOOLEAN NOT NULL,
    confidence_score DECIMAL(3,2) DEFAULT 0.95 CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    moderation_api VARCHAR(20) NOT NULL CHECK (moderation_api IN ('OPENAI', 'PERSPECTIVE', 'LOCAL')),
    last_checked TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days'),

    -- Indexes para búsqueda rápida
    UNIQUE(word, language)
);

COMMENT ON TABLE safe_words_cache IS 'Cache local de palabras verificadas por APIs de moderación';
COMMENT ON COLUMN safe_words_cache.confidence_score IS 'Score de confianza de la verificación (0.0-1.0)';
COMMENT ON COLUMN safe_words_cache.expires_at IS 'Fecha de expiración para re-verificación';
```

#### 3. generation_stats (Métricas)
```sql
CREATE TABLE generation_stats (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    language VARCHAR(5) NOT NULL,
    pattern_type VARCHAR(20) NOT NULL,
    total_generated INTEGER DEFAULT 0,
    total_served INTEGER DEFAULT 0,
    cache_hits INTEGER DEFAULT 0,
    cache_misses INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER DEFAULT 0,

    UNIQUE(date, language, pattern_type)
);

COMMENT ON TABLE generation_stats IS 'Estadísticas diarias de generación y performance';
```

### Índices Optimizados para R2DBC

#### 1. Índices de Búsqueda Primaria
```sql
-- Username lookup (más crítico)
CREATE UNIQUE INDEX idx_username_unique ON generated_usernames(username);

-- Available usernames por idioma (segunda prioridad)
CREATE INDEX idx_available_usernames
ON generated_usernames(language, is_used, created_at DESC)
WHERE is_used = FALSE;

-- Partial index para usernames no usados
CREATE INDEX idx_unused_usernames_by_lang
ON generated_usernames(language, created_at DESC)
WHERE is_used = FALSE;
```

#### 2. Índices de Performance
```sql
-- Búsqueda por patrón y categoría
CREATE INDEX idx_pattern_category
ON generated_usernames(pattern_type, category, language)
WHERE is_used = FALSE;

-- Índice temporal para limpieza de datos
CREATE INDEX idx_created_at ON generated_usernames(created_at);
CREATE INDEX idx_used_at ON generated_usernames(used_at) WHERE used_at IS NOT NULL;

-- Safe words cache lookups
CREATE INDEX idx_safe_words_lookup
ON safe_words_cache(word, language, is_safe, expires_at);

-- Cleanup de expired words
CREATE INDEX idx_safe_words_expires ON safe_words_cache(expires_at);
```

#### 3. Índices de Estadísticas
```sql
-- Stats queries
CREATE INDEX idx_stats_date_lang ON generation_stats(date DESC, language);
CREATE INDEX idx_stats_performance ON generation_stats(date DESC, avg_response_time_ms);
```

## ⚙️ Configuración R2DBC

### Dependencias Gradle
```gradle
dependencies {
    // R2DBC PostgreSQL
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    implementation 'org.postgresql:r2dbc-postgresql:1.0.2.RELEASE'

    // Para migrations (Flyway o Liquibase)
    implementation 'org.postgresql:postgresql:42.6.0'  // JDBC driver for migrations
    implementation 'org.flywaydb:flyway-core'

    // Connection pooling
    implementation 'io.r2dbc:r2dbc-pool'
}
```

## 📊 Migrations con Flyway

### V1__Initial_Schema.sql
```sql
-- NameForge Initial Schema
-- Version: 1.0.0
-- Date: 2024-09-24

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Main usernames table
CREATE TABLE generated_usernames (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    language VARCHAR(5) NOT NULL CHECK (language IN ('EN', 'ES')),
    pattern_type VARCHAR(20) NOT NULL CHECK (pattern_type IN ('CLASSIC', 'SEPARATOR', 'WORDPLAY')),
    category VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,

    CONSTRAINT chk_username_format CHECK (username ~ '^[a-z0-9_-]{5,30}$'),
    CONSTRAINT chk_username_length CHECK (char_length(username) BETWEEN 5 AND 30)
);

-- Safe words cache
CREATE TABLE safe_words_cache (
    word VARCHAR(50) PRIMARY KEY,
    language VARCHAR(5) NOT NULL CHECK (language IN ('EN', 'ES')),
    category VARCHAR(30) NOT NULL,
    is_safe BOOLEAN NOT NULL,
    confidence_score DECIMAL(3,2) DEFAULT 0.95 CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    moderation_api VARCHAR(20) NOT NULL CHECK (moderation_api IN ('OPENAI', 'PERSPECTIVE', 'LOCAL')),
    last_checked TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days'),

    UNIQUE(word, language)
);

-- Generation statistics
CREATE TABLE generation_stats (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    language VARCHAR(5) NOT NULL,
    pattern_type VARCHAR(20) NOT NULL,
    total_generated INTEGER DEFAULT 0,
    total_served INTEGER DEFAULT 0,
    cache_hits INTEGER DEFAULT 0,
    cache_misses INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER DEFAULT 0,

    UNIQUE(date, language, pattern_type)
);

-- Primary indexes
CREATE UNIQUE INDEX idx_username_unique ON generated_usernames(username);
CREATE INDEX idx_available_usernames ON generated_usernames(language, is_used, created_at DESC) WHERE is_used = FALSE;
CREATE INDEX idx_pattern_category ON generated_usernames(pattern_type, category, language) WHERE is_used = FALSE;
CREATE INDEX idx_created_at ON generated_usernames(created_at);

-- Safe words indexes
CREATE INDEX idx_safe_words_lookup ON safe_words_cache(word, language, is_safe, expires_at);
CREATE INDEX idx_safe_words_expires ON safe_words_cache(expires_at);

-- Stats indexes
CREATE INDEX idx_stats_date_lang ON generation_stats(date DESC, language);

-- Comments
COMMENT ON TABLE generated_usernames IS 'Almacena todos los usernames generados';
COMMENT ON TABLE safe_words_cache IS 'Cache de verificación de contenido';
COMMENT ON TABLE generation_stats IS 'Métricas de performance diarias';
```

### V2__Performance_Optimizations.sql
```sql
-- Performance optimizations
-- Version: 1.0.1

-- Optimized partial indexes for hot queries
CREATE INDEX CONCURRENTLY idx_unused_recent
ON generated_usernames(language, created_at DESC)
WHERE is_used = FALSE AND created_at > (CURRENT_DATE - INTERVAL '30 days');

-- Materialized view for dashboard stats
CREATE MATERIALIZED VIEW daily_generation_summary AS
SELECT
    date,
    language,
    SUM(total_generated) as total_generated,
    SUM(total_served) as total_served,
    ROUND(AVG(avg_response_time_ms)::numeric, 2) as avg_response_time,
    ROUND((SUM(cache_hits)::numeric / NULLIF(SUM(cache_hits + cache_misses), 0) * 100), 2) as cache_hit_rate
FROM generation_stats
GROUP BY date, language
ORDER BY date DESC, language;

CREATE UNIQUE INDEX idx_daily_summary ON daily_generation_summary(date, language);

-- Function for automatic cleanup
CREATE OR REPLACE FUNCTION cleanup_old_usernames() RETURNS void AS $$
BEGIN
    -- Archive old unused usernames (older than 90 days)
    DELETE FROM generated_usernames
    WHERE is_used = FALSE
      AND created_at < (CURRENT_DATE - INTERVAL '90 days');

    -- Clean expired safe words
    DELETE FROM safe_words_cache
    WHERE expires_at < CURRENT_TIMESTAMP;

    -- Refresh materialized view
    REFRESH MATERIALIZED VIEW daily_generation_summary;
END;
$$ LANGUAGE plpgsql;
```

## 🔧 Configuración de Performance

### Environment Variables
```yaml
# Connection Pool
DATABASE_INITIAL_SIZE=5
DATABASE_MAX_SIZE=20
DATABASE_MAX_IDLE_TIME=30m
DATABASE_MAX_LIFETIME=2h

# Query Timeouts
DATABASE_CONNECT_TIMEOUT=30s
DATABASE_STATEMENT_TIMEOUT=60s
DATABASE_LOCK_WAIT_TIMEOUT=10s

# SSL Configuration
DATABASE_SSL_MODE=require          # En producción
DATABASE_SSL_CERT_PATH=/certs/     # Certificados SSL
```

### Monitoring Queries
```sql
-- Active connections
SELECT count(*) as active_connections
FROM pg_stat_activity
WHERE state = 'active';

-- Slow queries
SELECT query, calls, mean_exec_time, max_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Index usage
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Cache hit ratio
SELECT
    sum(heap_blks_read) as heap_read,
    sum(heap_blks_hit) as heap_hit,
    round(sum(heap_blks_hit) / nullif(sum(heap_blks_hit) + sum(heap_blks_read), 0) * 100, 2) as hit_ratio
FROM pg_statio_user_tables;
```

## 🚨 Mantenimiento y Backup

### Scheduled Tasks
```sql
-- Daily cleanup job (via cron o scheduler)
SELECT cleanup_old_usernames();

-- Weekly VACUUM and ANALYZE
VACUUM ANALYZE generated_usernames;
VACUUM ANALYZE safe_words_cache;
VACUUM ANALYZE generation_stats;

-- Refresh materialized views
REFRESH MATERIALIZED VIEW CONCURRENTLY daily_generation_summary;
```

### Backup Strategy
```bash
# Backup completo diario
pg_dump -h localhost -U nameforge_user -d nameforge \
  --format=custom \
  --compress=9 \
  --file=/backups/nameforge_$(date +%Y%m%d).backup

# Backup incremental (WAL archiving)
# En postgresql.conf:
# wal_level = replica
# archive_mode = on
# archive_command = 'cp %p /backup/wal/%f'
```
