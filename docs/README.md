# NameForge Backend - Documentación Técnica

## 📋 Resumen del Proyecto

**NameForge** es un sistema backend de generación de nombres de usuario aleatorios estilo Reddit, construido con **Java 21**, **Spring Boot 3.2+**, **WebFlux**, **R2DBC** y **Redis**. Implementa **arquitectura hexagonal** con **principios SOLID** y **programación reactiva** para máximo rendimiento y escalabilidad.

### 🎯 Características Principales

- ✅ **Generación Aleatoria**: Algoritmos tipo Reddit (adjetivo + sustantivo + número)
- ✅ **Multiidioma**: Soporte para Inglés y Español
- ✅ **APIs Duales**: REST + GraphQL
- ✅ **Validación Completa**: Formato, unicidad, contenido apropiado
- ✅ **Cache Inteligente**: Redis multi-layer con Bloom Filter
- ✅ **Reactive Stack**: WebFlux + R2DBC para alta concurrencia
- ✅ **Arquitectura Hexagonal**: SOLID principles + clean architecture

### 📊 Métricas de Performance

```yaml
Throughput: 200+ requests/second
Latency: P95 < 100ms, P99 < 300ms
Memory: <512MB heap
Threads: ~10 reactive threads
Cache Hit Rate: >95%
Availability: 99.9% target
```

## 📚 Documentación Técnica

### 1. **[Arquitectura General](01-Architecture-Overview.md)**
- Visión general del sistema y componentes
- Stack tecnológico y patrones arquitecturales
- Principios SOLID aplicados
- Flujos de datos reactivos

### 2. **[Implementación de Base de Datos](02-Database-Implementation.md)**
- Schema PostgreSQL optimizado
- Configuración R2DBC reactiva
- Indexes para performance
- Migrations y mantenimiento

### 3. **[Implementación de Redis Cache](03-Redis-Implementation.md)**
- Estrategia multi-layer cache
- Bloom Filter para unicidad
- Cache reactivo con Spring Data
- Patrones de resilencia

### 4. **[Arquitectura Java](04-Java-Architecture.md)**
- Estructura hexagonal detallada
- Implementación de use cases
- Adapters y ports
- Configuración reactive

### 5. **[Especificaciones de APIs](05-API-Specifications.md)**
- REST API completa con OpenAPI
- GraphQL schema y resolvers
- Error handling y validaciones
- Especificaciones de seguridad

### 6. **[Deployment y Configuración](06-Deployment-Configuration.md)**
- Docker multi-stage builds
- Kubernetes manifests
- Configuración por environments
- Monitoring y observabilidad

## 🚀 Quick Start

### Prerrequisitos
```bash
- Java 21+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+
```

### Desarrollo Local
```bash
# 1. Clonar repositorio
git clone https://github.com/nameforge/backend.git
cd nameforge-backend

# 2. Iniciar servicios dependientes
docker-compose up -d postgres redis

# 3. Configurar variables de entorno
cp .env.example .env
# Editar .env con tus API keys

# 4. Ejecutar aplicación
./gradlew bootRun

# 5. Verificar funcionamiento
curl http://localhost:8080/api/v1/health
```

### Docker Deployment
```bash
# Build y deploy completo
docker-compose up --build

# Solo servicios core
docker-compose up nameforge-app postgres redis
```

### Kubernetes Deployment
```bash
# Deploy completo
kubectl apply -f k8s/

# Verificar estado
kubectl get pods -n nameforge
kubectl get services -n nameforge
```

## 🔧 Configuración

### Variables de Entorno Principales
```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=nameforge
DB_USERNAME=nameforge_user
DB_PASSWORD=your_db_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# APIs Externas
OPENAI_API_KEY=sk-your-openai-key
PERSPECTIVE_API_KEY=your-perspective-key

# Performance
HEAP_SIZE=512m
IO_WORKER_COUNT=4
BATCH_GENERATION_SIZE=1000
```

### Profiles Disponibles
```bash
# Desarrollo
SPRING_PROFILES_ACTIVE=dev

# Staging
SPRING_PROFILES_ACTIVE=staging

# Producción
SPRING_PROFILES_ACTIVE=production
```

## 🧪 Testing

### Tests Unitarios
```bash
./gradlew test
```

### Tests de Integración
```bash
./gradlew integrationTest
```

### Smoke Tests
```bash
./scripts/smoke-test.sh dev
```

### Load Testing
```bash
# Usando K6
k6 run tests/load/username-generation.js
```

## 📈 Monitoring

### Health Checks
```bash
# Application health
curl http://localhost:8081/actuator/health

# Detailed health
curl http://localhost:8081/actuator/health/db
curl http://localhost:8081/actuator/health/redis
```

### Métricas Prometheus
```bash
# Métricas de aplicación
curl http://localhost:8081/actuator/prometheus
```

### Dashboards Grafana
- Username Generation Metrics
- Cache Performance
- Database Performance
- API Response Times

## 🔒 Seguridad

### Rate Limiting
- 50 requests/second por IP
- 1000 requests/minute por IP
- Endpoints administrativos protegidos

### Validación de Input
- Formato de username: `[a-z0-9_-]{5,30}`
- Content moderation via OpenAI + Perspective APIs
- SQL injection prevention via R2DBC

### Headers de Seguridad
```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
```

## 🐛 Troubleshooting

### Problemas Comunes

#### High Memory Usage
```bash
# Verificar heap usage
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Ajustar heap size
export HEAP_SIZE=1g
```

#### Database Connection Issues
```bash
# Verificar conectividad
kubectl exec -it postgres-0 -- psql -U nameforge_user -d nameforge -c "SELECT 1;"

# Check pool status
curl http://localhost:8081/actuator/metrics/r2dbc.pool
```

#### Cache Performance Issues
```bash
# Verificar Redis conectividad
redis-cli -h localhost -p 6379 ping

# Check cache hit rate
curl http://localhost:8081/actuator/metrics/cache.gets
```

#### API Rate Limiting
```bash
# Verificar rate limits actuales
curl -I http://localhost:8080/api/v1/usernames/generate

# Headers de respuesta muestran límites
X-Rate-Limit-Remaining: 49
X-Rate-Limit-Reset: 1640995200
```

## 🔄 CI/CD Pipeline

### GitHub Actions
```yaml
# .github/workflows/ci.yml
- Build and test
- Security scanning
- Docker image build
- Deploy to staging
- Integration tests
- Deploy to production
```

### Deployment Estrategia
1. **Staging Deployment**: Automático en merge a `develop`
2. **Production Deployment**: Manual approval en merge a `main`
3. **Rollback**: Automático en health check failures

## 📞 Soporte

### Contactos
- **Technical Lead**: tech-lead@nameforge.com
- **DevOps Team**: devops@nameforge.com
- **API Support**: api-support@nameforge.com

### Recursos
- **GitHub Issues**: https://github.com/nameforge/backend/issues
- **Wiki**: https://wiki.nameforge.com
- **Slack**: #nameforge-support

### SLA y Escalación
- **P1 (Critical)**: 15 min response, 4 hours resolution
- **P2 (High)**: 2 hours response, 24 hours resolution
- **P3 (Medium)**: 8 hours response, 72 hours resolution
- **P4 (Low)**: 24 hours response, 1 week resolution

---

**Documentación generada**: 2024-09-24
**Versión**: 1.0.0
**Última actualización**: 2024-09-24