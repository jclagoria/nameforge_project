# ⚒️ NameForge - Reactive Username Generation System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-green.svg)](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A high-performance, reactive username generation system built with **Java 21**, **Spring Boot WebFlux**, and **Hexagonal Architecture**. NameForge generates creative, unique usernames with built-in content moderation and multi-layer caching.

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Running the Application](#-running-the-application)
- [API Documentation](#-api-documentation)
- [Testing](#-testing)
- [Project Structure](#-project-structure)
- [Performance](#-performance)
- [Contributing](#-contributing)

---

## ✨ Features

- 🚀 **Reactive & Non-Blocking**: Built with Spring WebFlux for high concurrency
- 🎯 **Intelligent Username Generation**: Multiple generation strategies (random, pattern-based)
- 🛡️ **Content Moderation**: Integrated with OpenAI and Google Perspective API
- ⚡ **Multi-Layer Caching**: Redis with Bloom filters for optimal performance
- 🔄 **Resilience Patterns**: Circuit breaker, retry, and timeout strategies with Resilience4j
- 🌐 **Dual API Support**: REST and GraphQL endpoints
- 🏗️ **Hexagonal Architecture**: Clean separation of concerns and domain-driven design
- 📊 **Observability**: Prometheus metrics, health checks, and detailed logging
- 🔒 **Security**: Built-in validation, rate limiting, and secure configuration

---

## 🏛️ Architecture

NameForge follows **Hexagonal Architecture** (Ports and Adapters pattern) with strict dependency rules:

```
┌─────────────────────────────────────────────────────────┐
│                    Adapters (Inbound)                   │
│            REST Controllers · GraphQL Resolvers          │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                  Domain Layer (Core)                    │
│        Use Cases · Business Logic · Entities            │
│              NO Framework Dependencies                  │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                  Adapters (Outbound)                    │
│   R2DBC Repositories · Redis Cache · External APIs      │
└─────────────────────────────────────────────────────────┘
```

**Key Principles:**
- Dependencies point inward (Domain has no outward dependencies)
- Reactive throughout (Mono/Flux from Project Reactor)
- SOLID principles strictly enforced

---

## 🛠️ Tech Stack

### Core Technologies
- **Java 21** - Latest LTS with virtual threads support
- **Spring Boot 3.5.6** - Framework foundation
- **Spring WebFlux** - Reactive web framework
- **Project Reactor** - Reactive programming library

### Data Layer
- **PostgreSQL 15+** - Primary database (Neon Cloud)
- **R2DBC** - Reactive database connectivity
- **Redis 7+** - Caching and Bloom filters (Upstash)
- **Flyway** - Database migrations

### External Services
- **OpenAI Moderation API** - Content filtering
- **Google Perspective API** - Toxicity detection
- **DataFaker** - Username pattern generation

### Resilience & Monitoring
- **Resilience4j** - Circuit breaker, retry, rate limiter
- **Micrometer** - Metrics collection
- **Prometheus** - Metrics export
- **Spring Actuator** - Health checks and monitoring

### Build & Testing
- **Gradle 8+** - Build automation
- **JUnit 5** - Unit testing
- **Testcontainers** - Integration testing
- **WireMock** - API mocking

---

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21** or higher ([Download](https://adoptium.net/))
- **Docker & Docker Compose** (for local development)
- **Gradle 8+** (or use included wrapper)
- **Git**

### External Services (Optional)

For full functionality, you'll need accounts for:
- [Neon PostgreSQL](https://console.neon.tech/) - Free tier available
- [Upstash Redis](https://console.upstash.com/) - Free tier available
- [OpenAI API](https://platform.openai.com/api-keys) - Optional for moderation
- [Google Perspective API](https://developers.perspectiveapi.com/s/docs-get-started) - Optional for moderation

---

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/nameforge.git
cd nameforge/forge-name-project
```

### 2. Configure Environment Variables

Create a `.env` file from the example:

```bash
cp .env_example .env
```

Edit `.env` with your actual credentials:

```bash
# Database (Neon PostgreSQL)
DB_HOST=your-endpoint.region.aws.neon.tech
DB_NAME=your_database_name
DB_USERNAME=your_database_user
DB_PASSWORD=your_database_password

# Redis (Upstash)
REDIS_HOST=your-redis-instance.upstash.io
REDIS_PASSWORD=your_redis_password_token

# Moderation APIs (Optional)
OPENAI_API_KEY=sk-proj-your_openai_api_key_here
PERSPECTIVE_API_KEY=your_perspective_api_key_here
```

### 3. Database Setup

The database schema is managed by Flyway migrations. They will run automatically on application startup.

**Manual migration (optional):**
```bash
./gradlew flywayMigrate
```

---

## ⚙️ Configuration

### Application Profiles

The application supports multiple profiles:

- **`development`** (default) - Local development with relaxed settings
- **`test`** - Test profile with in-memory/test containers
- **`local`** - Docker Compose local deployment
- **`production`** - Production with strict settings

Set the active profile:
```bash
export SPRING_PROFILES_ACTIVE=development
```

### Configuration Files

- `application.yml` - Base configuration
- `application-development.yml` - Development overrides
- `application-production.yml` - Production settings
- `application-test.yml` - Test configuration

### Key Configuration Options

**Resilience4j Circuit Breaker:**
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

**Moderation Thresholds:**
```yaml
moderation:
  perspective:
    thresholds:
      toxicity: 0.7
      severeToxicity: 0.5
      identityAttack: 0.6
```

---

## 🏃 Running the Application

### Option 1: Local Development (with Docker dependencies)

**Start PostgreSQL and Redis:**
```bash
docker-compose up -d postgres redis
```

**Run the application:**
```bash
./gradlew bootRun
```

The application will be available at:
- **API**: http://localhost:8080
- **Actuator**: http://localhost:8081/actuator
- **Health**: http://localhost:8081/actuator/health

### Option 2: Full Docker Compose

**Start all services (app + dependencies):**
```bash
docker-compose up -d
```

**View logs:**
```bash
docker-compose logs -f nameforge-app
```

**Stop all services:**
```bash
docker-compose down
```

### Option 3: Build and Run JAR

**Build the application:**
```bash
./gradlew clean build
```

**Run the JAR:**
```bash
java -jar build/libs/forge-name-project-0.0.1-SNAPSHOT.jar
```

---

## 📡 API Documentation

### REST API

#### Generate Usernames
```bash
POST /api/v1/usernames/generate
Content-Type: application/json

{
  "language": "EN",
  "count": 5,
  "patternType": "RANDOM"
}
```

**Response:**
```json
{
  "usernames": [
    "cosmic_tiger_2024",
    "azure_falcon_919",
    "quantum_wolf_573"
  ],
  "language": "EN",
  "generatedAt": "2025-01-19T10:30:00Z"
}
```

#### Validate Username
```bash
GET /api/v1/usernames/validate/cosmic_tiger?language=EN
```

**Response:**
```json
{
  "username": "cosmic_tiger",
  "isValid": true,
  "isAvailable": true,
  "isAppropriate": true
}
```

### GraphQL API

**Endpoint:** `POST /graphql`

**Generate Usernames:**
```graphql
mutation {
  generateUsernames(request: {
    language: EN
    count: 3
    patternType: RANDOM
  }) {
    usernames
    language
    generatedAt
  }
}
```

**Interactive GraphiQL:** http://localhost:8080/graphiql (development only)

### OpenAPI/Swagger Documentation

Interactive API documentation available at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

---

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### Run Integration Tests Only
```bash
./gradlew integrationTest
```

### Run Specific Test Class
```bash
./gradlew test --tests "*OpenAIModerationService*"
```

### Test Coverage Report
```bash
./gradlew test jacocoTestReport
```
View report at: `build/reports/jacoco/test/html/index.html`

### Test Categories

- **Unit Tests**: Fast, isolated tests with mocked dependencies
- **Integration Tests**: Testcontainers with real PostgreSQL and Redis
- **WireMock Tests**: External API integration testing
- **Contract Tests**: API contract verification

---

## 📁 Project Structure

```
forge-name-project/
├── src/main/java/com/forge/
│   ├── application/           # Application entry point
│   ├── domain/                # Core business logic (framework-agnostic)
│   │   ├── model/            # Entities and value objects
│   │   ├── ports/            # Interfaces (inbound/outbound)
│   │   └── usecases/         # Use case implementations
│   ├── adapters/             # Framework integrations
│   │   ├── inbound/          # REST/GraphQL controllers
│   │   │   ├── rest/
│   │   │   ├── graphql/
│   │   │   └── dto/
│   │   └── outbound/         # External system implementations
│   │       ├── database/     # R2DBC repositories
│   │       ├── cache/        # Redis implementations
│   │       ├── moderation/   # OpenAI/Perspective clients
│   │       └── generation/   # DataFaker integration
│   └── infrastructure/       # Cross-cutting concerns
│       ├── config/           # Spring configuration
│       ├── resilience/       # Resilience4j setup
│       └── properties/       # Configuration properties
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/         # Flyway migrations
│   └── graphql/              # GraphQL schemas
├── src/test/                 # Test mirror structure
├── docs/                     # Technical documentation
├── docker-compose.yml
├── .env_example
└── README.md
```

---

## ⚡ Performance

### Target Metrics

- **Throughput**: 200+ requests/second
- **Latency**:
  - P95 < 100ms
  - P99 < 300ms
- **Memory**: < 512MB heap
- **Cache Hit Rate**: > 95%

### Optimization Strategies

1. **Reactive Streams**: Non-blocking I/O throughout the stack
2. **Multi-Layer Caching**: Redis + Bloom filters reduce database load
3. **Connection Pooling**: Optimized R2DBC connection pool
4. **Circuit Breaker**: Prevents cascading failures
5. **Batch Operations**: Efficient bulk username generation

### Monitoring Endpoints

- **Health Check**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus`
- **Info**: `GET /actuator/info`

---

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Code Standards

- Follow **Java Code Conventions**
- Maintain **100% test coverage** for domain layer
- Use **reactive patterns** (Mono/Flux)
- Follow **hexagonal architecture** principles
- Add **Javadoc** for public APIs

### Testing Requirements

- All new features must include unit tests
- Integration tests for database operations
- WireMock tests for external API calls

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [Spring Team](https://spring.io/) - For the amazing reactive framework
- [Resilience4j](https://resilience4j.readme.io/) - For resilience patterns
- [Neon](https://neon.tech/) - For serverless PostgreSQL
- [Upstash](https://upstash.com/) - For serverless Redis

---

## 📞 Support

For questions or issues:
- 📧 Email: support@nameforge.example.com
- 🐛 Issues: [GitHub Issues](https://github.com/yourusername/nameforge/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/yourusername/nameforge/discussions)

---

**Built with ❤️ using Spring Boot WebFlux and Hexagonal Architecture**
