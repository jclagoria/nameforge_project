# NameForge Backend - Arquitectura Técnica General

## 📋 Información del Proyecto

**Proyecto**: NameForge - Generador de Nombres de Usuario
**Versión**: 1.0.0
**Arquitectura**: Hexagonal + Reactive
**Fecha**: 2024-09-24

## 🎯 Objetivos del Sistema

### Funcionales
- Generación de nombres de usuario aleatorios estilo Reddit
- Soporte multiidioma (Inglés/Español)
- Validación de unicidad en tiempo real
- Filtrado de contenido inapropiado
- APIs REST y GraphQL

### No Funcionales
- **Performance**: 200+ req/sec throughput
- **Latencia**: <100ms promedio de respuesta
- **Escalabilidad**: Arquitectura reactiva non-blocking
- **Mantenibilidad**: Principios SOLID + Hexagonal
- **Disponibilidad**: 99.9% uptime target

## 🏗️ Arquitectura de Alto Nivel

### Stack Tecnológico Principal
```yaml
Framework: Spring Boot 3.2+ WebFlux
Database: PostgreSQL 15+ con R2DBC
Cache: Redis 7+ Reactive
APIs: REST + GraphQL
Language: Java 21
Build: Gradle 8+
```

### Patrón Arquitectural: Hexagonal + Reactive

```
┌─────────────────────────────────────────────────────────────────┐
│                        INBOUND ADAPTERS                        │
├─────────────────────────────────────────────────────────────────┤
│  REST Controller  │  GraphQL Resolver  │  Health Endpoints     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────────────┐
│                      DOMAIN CORE                               │
├─────────────────────────────────────────────────────────────────┤
│                    INBOUND PORTS                               │
│  ┌─────────────────┐  ┌─────────────────┐                     │
│  │ Generation      │  │ Validation      │                     │
│  │ Use Case        │  │ Use Case        │                     │
│  └─────────────────┘  └─────────────────┘                     │
├─────────────────────────────────────────────────────────────────┤
│                   DOMAIN ENTITIES                              │
│  Username • GenerationRequest • ValidationResult              │
├─────────────────────────────────────────────────────────────────┤
│                    OUTBOUND PORTS                              │
│  Repository • CacheService • ModerationService • Generator    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────────────┐
│                       OUTBOUND ADAPTERS                        │
├─────────────────────────────────────────────────────────────────┤
│ R2DBC Repo │ Redis Cache │ OpenAI API │ DataFaker │ Perspective │
└─────────────────────────────────────────────────────────────────┘
```

### Flujo de Datos Reactivo
```
HTTP Request → Controller → Use Case → Repository/Cache → External APIs
     ↓              ↓           ↓            ↓              ↓
  WebFlux        Mono/Flux   Domain      R2DBC/Redis    WebClient
                                        Reactive      Non-blocking
```

## 🔄 Principios SOLID Aplicados

### Single Responsibility
- **Use Cases**: Una responsabilidad específica (generación o validación)
- **Repositories**: Solo operaciones de persistencia
- **Services**: Solo lógica de negocio específica
- **Controllers**: Solo mapeo HTTP/GraphQL

### Open/Closed
- **Strategy Pattern**: Algoritmos de generación intercambiables
- **Port/Adapter**: Nuevas implementaciones sin modificar core
- **Configuration**: Extensible vía environment variables

### Liskov Substitution
- **Moderation Services**: APIs intercambiables (OpenAI ↔ Perspective)
- **Cache Implementations**: Redis ↔ In-Memory transparente
- **Repository Implementations**: Test mocks ↔ Production

### Interface Segregation
- **Ports Específicos**: `UsernameRepository`, `CacheService`, `ModerationService`
- **No Interfaces Gordas**: Cada port con responsabilidad única
- **Client-Specific**: Interfaces diseñadas para sus usuarios

### Dependency Inversion
- **Use Cases**: Dependen de ports (abstractions), no adapters
- **Dependency Injection**: Spring maneja wiring automático
- **Configuration**: Infrastructure layer maneja implementaciones

## 📊 Modelo de Dominio

### Entidades Principales
```java
// Value Objects
public record Username(String value, Language language, PatternType pattern) {}
public record GenerationRequest(Language language, int count) {}
public record ValidationResult(boolean isUnique, boolean isAppropriate, List<String> reasons) {}

// Enums
public enum Language { EN, ES }
public enum PatternType { CLASSIC, SEPARATOR, WORDPLAY }
public enum Category { ANIMALS, COLORS, TECHNOLOGY, NATURE }
```

### Agregados y Invariantes

- **Username Aggregate**: Asegura formato válido (5-30 chars, charset específico)
- **Generation Aggregate**: Valida count máximo (10), language requerido
- **Uniqueness Invariant**: No duplicados en sistema
- **Content Appropriateness**: Filtrado obligatorio pre-storage

## 🚀 Patrones de Implementación

### Reactive Programming

### Circuit Breaker Pattern

### Strategy Pattern

## 📈 Métricas de Performance

### Targets de Performance
```yaml
Throughput: 200+ requests/second
Latency:
  - P50: <50ms
  - P95: <100ms
  - P99: <300ms
Memory: <512MB heap
Threads: ~10 reactive threads
Connection Pool: 20 DB connections
Cache Hit Ratio: >95%
```

### Escalabilidad Reactiva
- **Non-blocking I/O**: Netty event loops
- **Backpressure**: Reactor built-in handling
- **Resource Efficiency**: O(1) threads vs requests
- **Memory Efficiency**: Streaming vs collecting

## 🔒 Consideraciones de Seguridad

### Validación de Input
- **Request Validation**: Bean validation en DTOs
- **SQL Injection**: R2DBC prepared statements
- **XSS Prevention**: Content sanitization
- **Rate Limiting**: Per-IP request throttling

### Content Moderation
- **Primary**: OpenAI Moderation API
- **Secondary**: Google Perspective API
- **Fallback**: Local safe words cache
- **Circuit breaker**: Fault tolerance

## 📦 Estructura de Módulos

```
nameforge-backend/
├── domain/                 # Core business logic
│   ├── model/             # Entities & Value Objects
│   ├── ports/             # Inbound & Outbound interfaces
│   └── usecases/          # Business use cases
├── adapters/              # External world integration
│   ├── inbound/           # REST & GraphQL
│   └── outbound/          # DB, Cache, APIs
├── infrastructure/        # Configuration & utilities
└── application/           # Main application class
```

## 🔧 Configuración y Deployment

### Environment Variables
```yaml
Database: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
Redis: REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
APIs: OPENAI_API_KEY, PERSPECTIVE_API_KEY
Performance: BATCH_SIZE, CACHE_TTL, RATE_LIMIT
```

### Health Checks
- Database connectivity (R2DBC)
- Redis availability (ReactiveRedisTemplate)
- External APIs status (Circuit breaker metrics)
- Memory usage and GC metrics

## 🚨 Deuda Técnica Identificada

### Limitaciones Actuales
1. **Combinaciones finitas**: ~10M usernames posibles
2. **Escalabilidad de categorías**: Require refactoring para expansión
3. **Wordplay patterns**: Solo básicos implementados
4. **Internacionalización**: Limited pun/wordplay translation

### Futuras Mejoras
1. **ML Content Detection**: Replace external APIs
2. **Dynamic Categories**: Runtime category management
3. **Advanced Algorithms**: Markov chains, neural generation
4. **Multi-region deployment**: Global distribution

## 📋 Documentos Relacionados

1. **Database Implementation** → `02-Database-Implementation.md`
2. **Redis Cache Implementation** → `03-Redis-Implementation.md`
3. **Java Architecture Details** → `04-Java-Architecture.md`
4. **API Specifications** → `05-API-Specifications.md`
5. **Deployment Guide** → `06-Deployment-Configuration.md`
