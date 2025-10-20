# NameForge Backend - Implementación de Redis Cache

## 📋 Información del Documento

**Proyecto**: NameForge - Cache Layer
**Tecnología**: Redis 7+ con Spring Data Reactive Redis
**Paradigma**: Reactive Caching
**Fecha**: 2024-09-24

## 🚀 Arquitectura de Cache

### Estrategia Multi-Layer Cache
```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   Application       │    │    Redis Cache      │    │   PostgreSQL        │
│   (Caffeine L1)     │───▶│   (L2 Distributed)  │───▶│   (Authoritative)   │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘
         1-5ms                      5-20ms                      50-200ms
```

### Tipos de Cache Implementados

#### 1. **Available Usernames Cache**
- **Propósito**: Cache de usernames listos para servir
- **Estructura**: Redis Lists por idioma
- **TTL**: 1 hora
- **Max Size**: 10,000 usernames por idioma

#### 2. **Safe Words Cache**
- **Propósito**: Cache de verificación de contenido
- **Estructura**: Redis Hash por idioma + categoría
- **TTL**: 24 horas
- **Backup**: PostgreSQL safe_words_cache table

#### 3. **Bloom Filter Cache**
- **Propósito**: Fast negative lookups para unicidad
- **Estructura**: Redis BitField (probabilistic)
- **TTL**: Sin expiración (rebuild diario)
- **False Positive Rate**: ~0.1%

#### 4. **Generation Stats Cache**
- **Propósito**: Métricas en tiempo real
- **Estructura**: Redis Hash con counters
- **TTL**: 1 día
- **Agregación**: Hourly rollups

## ⚙️ Configuración Reactive Redis

### Dependencias
```gradle
dependencies {
    // Redis Reactive
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.lettuce:lettuce-core:6.2.6.RELEASE'

    // Local cache (L1)
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'

    // JSON serialization
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
}
```

## 🔧 Cache Configuration Properties

### application.yml
```yaml
# Redis Configuration
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: ${REDIS_DATABASE:0}
    timeout: 10s

    # Lettuce pool configuration
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 30s

    # Cluster configuration (for production)
    cluster:
      nodes: ${REDIS_CLUSTER_NODES:}
      max-redirects: 3

# Custom cache configuration
nameforge:
  cache:
    usernames:
      ttl: 1h
      max-size-per-language: 10000
      preload-batch-size: 1000

    safe-words:
      ttl: 24h
      cleanup-interval: 1h
      confidence-threshold: 0.8

    bloom-filter:
      expected-elements: 10000000
      false-positive-rate: 0.001
      rebuild-interval: 24h

    local:
      enabled: true
      max-size: 1000
      ttl: 5m

    stats:
      ttl: 24h
      aggregation-interval: 1h
```

---
**Documento generado**: 2024-09-24
**Autor**: Cache Team
**Versión**: 1.0.0