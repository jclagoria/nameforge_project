# NameForge Backend - Deployment y Configuración

## 📋 Información del Documento

**Proyecto**: NameForge - Deployment & Operations
**Infraestructura**: Docker + Kubernetes
**Cloud Provider**: AWS/GCP/Azure Agnostic
**Fecha**: 2024-09-24

## 🐳 Docker Configuration

### Dockerfile (Multi-stage)
```dockerfile
# Build stage
FROM openjdk:21-jdk-slim AS build

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy build files
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/

# Build application
RUN chmod +x gradlew && \
    ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim AS runtime

# Add non-root user for security
RUN groupadd -r nameforge && useradd -r -g nameforge nameforge

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy built application
COPY --from=build /app/build/libs/nameforge-*.jar app.jar
COPY --chown=nameforge:nameforge docker/entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

# Application configuration
EXPOSE 8080 8081
USER nameforge

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["/entrypoint.sh"]
```

### Docker Entrypoint Script
```bash
#!/bin/bash
# docker/entrypoint.sh

set -e

# JVM configuration for containers
export JAVA_OPTS="${JAVA_OPTS} \
    -server \
    -XX:+UseZGC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Xms${HEAP_SIZE:-256m} \
    -Xmx${HEAP_SIZE:-512m} \
    -XX:MetaspaceSize=128m \
    -XX:MaxMetaspaceSize=256m \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

# Reactive Netty optimizations
export JAVA_OPTS="${JAVA_OPTS} \
    -Dreactor.netty.ioWorkerCount=${IO_WORKER_COUNT:-4} \
    -Dreactor.netty.ioSelectCount=${IO_SELECT_COUNT:-2} \
    -Dio.netty.allocator.type=pooled \
    -Dio.netty.recycler.maxCapacity.default=256"

# Security configurations
export JAVA_OPTS="${JAVA_OPTS} \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Wait for dependencies
echo "Waiting for dependencies..."
/wait-for-it.sh ${DB_HOST:-postgres}:${DB_PORT:-5432} --timeout=60 --strict -- echo "Database is up"
/wait-for-it.sh ${REDIS_HOST:-redis}:${REDIS_PORT:-6379} --timeout=60 --strict -- echo "Redis is up"

# Start application
echo "Starting NameForge application..."
echo "JVM Options: $JAVA_OPTS"

exec java $JAVA_OPTS -jar app.jar "$@"
```

### Docker Compose (Development)
```yaml
version: '3.8'

services:
  nameforge-app:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    ports:
      - "8080:8080"  # Application
      - "8081:8081"  # Management
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: nameforge
      DB_USERNAME: nameforge_user
      DB_PASSWORD: dev_password
      REDIS_HOST: redis
      REDIS_PORT: 6379
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      HEAP_SIZE: 512m
      IO_WORKER_COUNT: 4
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - nameforge-network

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: nameforge
      POSTGRES_USER: nameforge_user
      POSTGRES_PASSWORD: dev_password
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --lc-collate=C --lc-ctype=C"
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nameforge_user -d nameforge"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    networks:
      - nameforge-network

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
    restart: unless-stopped
    networks:
      - nameforge-network

  # Monitoring stack (optional)
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    networks:
      - nameforge-network

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./docker/grafana/dashboards:/etc/grafana/provisioning/dashboards
    networks:
      - nameforge-network

volumes:
  postgres_data:
  redis_data:
  prometheus_data:
  grafana_data:

networks:
  nameforge-network:
    driver: bridge
```

## ☸️ Kubernetes Deployment

### Namespace and ConfigMap
```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: nameforge
  labels:
    app: nameforge
    version: v1.0.0

---
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nameforge-config
  namespace: nameforge
data:
  application.yml: |
    spring:
      application:
        name: nameforge
      profiles:
        active: production

      r2dbc:
        url: r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
        pool:
          initial-size: 5
          max-size: 20
          max-idle-time: 30m

      redis:
        host: ${REDIS_HOST}
        port: ${REDIS_PORT}
        password: ${REDIS_PASSWORD}
        timeout: 10s
        lettuce:
          pool:
            max-active: 20
            max-idle: 10
            min-idle: 5

    server:
      port: 8080
      shutdown: graceful

    management:
      server:
        port: 8081
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: when-authorized

    nameforge:
      generation:
        batch-size: 1000
        min-cache-threshold: 1000
        rate-limit-per-second: 50
      cache:
        username-ttl: 1h
        safe-words-ttl: 24h
        max-size-per-language: 10000
      moderation:
        timeout: 5s
        circuit-breaker-enabled: true
        confidence-threshold: 0.8

    logging:
      level:
        com.nameforge: INFO
        org.springframework.r2dbc: DEBUG
        io.r2dbc.postgresql: DEBUG
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### Secrets
```yaml
# k8s/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: nameforge-secrets
  namespace: nameforge
type: Opaque
stringData:
  DB_PASSWORD: "production_db_password"
  REDIS_PASSWORD: "production_redis_password"
  OPENAI_API_KEY: "sk-your-openai-api-key"
  PERSPECTIVE_API_KEY: "your-perspective-api-key"
```

### Application Deployment
```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nameforge-app
  namespace: nameforge
  labels:
    app: nameforge
    component: backend
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: nameforge
      component: backend
  template:
    metadata:
      labels:
        app: nameforge
        component: backend
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8081"
    spec:
      serviceAccountName: nameforge-sa
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000

      containers:
      - name: nameforge
        image: nameforge/backend:1.0.0
        imagePullPolicy: IfNotPresent

        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: management
          containerPort: 8081
          protocol: TCP

        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: HEAP_SIZE
          value: "1g"
        - name: IO_WORKER_COUNT
          value: "8"
        - name: IO_SELECT_COUNT
          value: "4"

        # Database configuration
        - name: DB_HOST
          value: "postgres-service"
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: "nameforge"
        - name: DB_USERNAME
          value: "nameforge_user"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: DB_PASSWORD

        # Redis configuration
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: REDIS_PASSWORD

        # API keys
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: OPENAI_API_KEY
        - name: PERSPECTIVE_API_KEY
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: PERSPECTIVE_API_KEY

        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"

        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

        lifecycle:
          preStop:
            exec:
              command: ["/bin/bash", "-c", "sleep 15"]

        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          runAsUser: 1000
          capabilities:
            drop:
            - ALL

        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: temp-volume
          mountPath: /tmp

      volumes:
      - name: config-volume
        configMap:
          name: nameforge-config
      - name: temp-volume
        emptyDir: {}

      terminationGracePeriodSeconds: 30
      restartPolicy: Always

---
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: nameforge-service
  namespace: nameforge
  labels:
    app: nameforge
    component: backend
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 80
    targetPort: 8080
    protocol: TCP
  - name: management
    port: 8081
    targetPort: 8081
    protocol: TCP
  selector:
    app: nameforge
    component: backend

---
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: nameforge-ingress
  namespace: nameforge
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - api.nameforge.com
    secretName: nameforge-tls
  rules:
  - host: api.nameforge.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nameforge-service
            port:
              number: 80
```

### Database Deployment (PostgreSQL)
```yaml
# k8s/postgres.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: nameforge
spec:
  serviceName: postgres-service
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
          name: postgres
        env:
        - name: POSTGRES_DB
          value: nameforge
        - name: POSTGRES_USER
          value: nameforge_user
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: DB_PASSWORD
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata

        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"

        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
        - name: postgres-config
          mountPath: /etc/postgresql/postgresql.conf
          subPath: postgresql.conf
          readOnly: true

        livenessProbe:
          exec:
            command:
            - /bin/sh
            - -c
            - exec pg_isready -U nameforge_user -d nameforge -h 127.0.0.1 -p 5432
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 6

        readinessProbe:
          exec:
            command:
            - /bin/sh
            - -c
            - exec pg_isready -U nameforge_user -d nameforge -h 127.0.0.1 -p 5432
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

      volumes:
      - name: postgres-config
        configMap:
          name: postgres-config

  volumeClaimTemplates:
  - metadata:
      name: postgres-storage
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: "ssd"
      resources:
        requests:
          storage: 20Gi

---
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
  namespace: nameforge
spec:
  ports:
  - port: 5432
    targetPort: 5432
  selector:
    app: postgres
```

### Redis Deployment
```yaml
# k8s/redis.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: nameforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command:
        - redis-server
        - /etc/redis/redis.conf
        ports:
        - containerPort: 6379
          name: redis

        env:
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nameforge-secrets
              key: REDIS_PASSWORD

        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"

        volumeMounts:
        - name: redis-config
          mountPath: /etc/redis/redis.conf
          subPath: redis.conf
          readOnly: true
        - name: redis-data
          mountPath: /data

        livenessProbe:
          exec:
            command:
            - redis-cli
            - ping
          initialDelaySeconds: 30
          periodSeconds: 10

        readinessProbe:
          exec:
            command:
            - redis-cli
            - ping
          initialDelaySeconds: 5
          periodSeconds: 5

      volumes:
      - name: redis-config
        configMap:
          name: redis-config
      - name: redis-data
        emptyDir: {}

---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
  namespace: nameforge
spec:
  ports:
  - port: 6379
    targetPort: 6379
  selector:
    app: redis
```

## 🔧 Environment Configuration

### Development Environment
```yaml
# config/application-dev.yml
spring:
  profiles:
    active: dev

  r2dbc:
    url: r2dbc:postgresql://localhost:5432/nameforge_dev
    username: dev_user
    password: dev_password
    pool:
      initial-size: 2
      max-size: 10

  redis:
    host: localhost
    port: 6379
    database: 0

nameforge:
  generation:
    batch-size: 100
    rate-limit-per-second: 100
  cache:
    username-ttl: 10m
    local-cache-enabled: true
  moderation:
    timeout: 10s
    circuit-breaker-enabled: false

logging:
  level:
    com.nameforge: DEBUG
    org.springframework.r2dbc: DEBUG
    reactor.netty: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Staging Environment
```yaml
# config/application-staging.yml
spring:
  profiles:
    active: staging

  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 5
      max-size: 15

  redis:
    host: ${REDIS_HOST}
    port: 6379
    password: ${REDIS_PASSWORD}
    lettuce:
      pool:
        max-active: 15
        max-idle: 8
        min-idle: 3

nameforge:
  generation:
    batch-size: 500
    rate-limit-per-second: 25
  cache:
    username-ttl: 30m
    max-size-per-language: 5000
  moderation:
    timeout: 5s
    circuit-breaker-enabled: true

logging:
  level:
    com.nameforge: INFO
    org.springframework.r2dbc: WARN
```

### Production Environment
```yaml
# config/application-prod.yml
spring:
  profiles:
    active: production

  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 10
      max-size: 30
      max-idle-time: 30m
      max-acquire-time: 30s
      max-create-connection-time: 30s
      validation-query: SELECT 1

  redis:
    host: ${REDIS_HOST}
    port: 6379
    password: ${REDIS_PASSWORD}
    timeout: 10s
    lettuce:
      pool:
        max-active: 30
        max-idle: 15
        min-idle: 10
        max-wait: 10s

server:
  port: 8080
  shutdown: graceful
  tomcat:
    threads:
      min-spare: 10
      max: 200

management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true

nameforge:
  generation:
    batch-size: 1000
    min-cache-threshold: 1000
    rate-limit-per-second: 50
  cache:
    username-ttl: 1h
    safe-words-ttl: 24h
    max-size-per-language: 10000
    local-cache-enabled: true
  moderation:
    timeout: 5s
    circuit-breaker-enabled: true
    confidence-threshold: 0.8

logging:
  level:
    com.nameforge: INFO
    org.springframework: WARN
    reactor: WARN
  pattern:
    file: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/nameforge/application.log
    max-size: 100MB
    max-history: 30
```

## 📊 Monitoring and Observability

### Prometheus Configuration
```yaml
# docker/prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "nameforge_rules.yml"

scrape_configs:
  - job_name: 'nameforge-app'
    static_configs:
      - targets: ['nameforge-app:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres:9187']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis:9121']

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### Grafana Dashboard
```json
{
  "dashboard": {
    "title": "NameForge Application Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_total[5m])",
            "legendFormat": "{{uri}} - {{method}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "http_server_requests_duration_seconds{quantile=\"0.95\"}",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "title": "Cache Hit Rate",
        "type": "singlestat",
        "targets": [
          {
            "expr": "cache_hit_rate",
            "legendFormat": "Cache Hit Rate"
          }
        ]
      },
      {
        "title": "Database Connections",
        "type": "graph",
        "targets": [
          {
            "expr": "r2dbc_pool_acquired_connections",
            "legendFormat": "Active Connections"
          }
        ]
      }
    ]
  }
}
```

### Health Check Configuration
```yaml
# Custom health indicators
management:
  health:
    custom:
      enabled: true
    components:
      nameforge-cache:
        enabled: true
      nameforge-database:
        enabled: true
      nameforge-moderation:
        enabled: true
```

## 🚀 Deployment Scripts

### Build and Deploy Script
```bash
#!/bin/bash
# scripts/deploy.sh

set -e

# Configuration
PROJECT_NAME="nameforge"
NAMESPACE="nameforge"
IMAGE_TAG=${1:-latest}
ENVIRONMENT=${2:-staging}

echo "🚀 Deploying NameForge $IMAGE_TAG to $ENVIRONMENT"

# Build Docker image
echo "📦 Building Docker image..."
docker build -t $PROJECT_NAME/backend:$IMAGE_TAG .

# Tag for registry
if [ "$ENVIRONMENT" == "production" ]; then
    REGISTRY="registry.nameforge.com"
    docker tag $PROJECT_NAME/backend:$IMAGE_TAG $REGISTRY/$PROJECT_NAME/backend:$IMAGE_TAG
    docker push $REGISTRY/$PROJECT_NAME/backend:$IMAGE_TAG
fi

# Deploy to Kubernetes
echo "☸️ Deploying to Kubernetes..."
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml

# Update deployment with new image
kubectl set image deployment/nameforge-app nameforge=$PROJECT_NAME/backend:$IMAGE_TAG -n $NAMESPACE

# Wait for rollout
echo "⏳ Waiting for rollout to complete..."
kubectl rollout status deployment/nameforge-app -n $NAMESPACE --timeout=300s

# Verify deployment
echo "✅ Verifying deployment..."
kubectl get pods -n $NAMESPACE
kubectl get services -n $NAMESPACE
kubectl get ingress -n $NAMESPACE

echo "🎉 Deployment completed successfully!"

# Run smoke tests
echo "🧪 Running smoke tests..."
./scripts/smoke-test.sh $ENVIRONMENT
```

### Smoke Test Script
```bash
#!/bin/bash
# scripts/smoke-test.sh

set -e

ENVIRONMENT=${1:-staging}

case $ENVIRONMENT in
    "dev")
        BASE_URL="http://localhost:8080"
        ;;
    "staging")
        BASE_URL="https://staging-api.nameforge.com"
        ;;
    "production")
        BASE_URL="https://api.nameforge.com"
        ;;
esac

echo "🧪 Running smoke tests against $BASE_URL"

# Health check
echo "🔍 Testing health endpoint..."
curl -f "$BASE_URL/api/v1/health" || exit 1

# Username generation
echo "🔍 Testing username generation..."
response=$(curl -s -X POST "$BASE_URL/api/v1/usernames/generate" \
    -H "Content-Type: application/json" \
    -d '{"language": "EN", "count": 3}')

echo "Response: $response"

# Check if response contains usernames array
if [[ $response == *"usernames"* ]]; then
    echo "✅ Username generation test passed"
else
    echo "❌ Username generation test failed"
    exit 1
fi

# Username validation
echo "🔍 Testing username validation..."
curl -f "$BASE_URL/api/v1/usernames/validate/testuser123?language=EN" || exit 1

echo "🎉 All smoke tests passed!"
```

---
**Documento generado**: 2024-09-24
**Autor**: DevOps Team
**Versión**: 1.0.0