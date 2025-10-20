# NameForge Backend - Arquitectura Java y Implementación

## 📋 Información del Documento

**Proyecto**: NameForge - Java Application Layer
**Tecnología**: Java 21 + Spring Boot 3.2+ + WebFlux
**Paradigma**: Hexagonal Architecture + Reactive Programming
**Fecha**: 2024-09-24

## 🏗️ Estructura del Proyecto

### Organización de Paquetes (Hexagonal)
```
src/main/java/com/nameforge/
├── domain/                          # 🎯 CORE DOMAIN
│   ├── model/                       # Entidades y Value Objects
│   │   ├── Username.java
│   │   ├── GenerationRequest.java
│   │   ├── ValidationResult.java
│   │   ├── Language.java
│   │   └── PatternType.java
│   ├── ports/                       # 🔌 PORTS (Interfaces)
│   │   ├── inbound/                 # Use Case interfaces (driving ports)
│   │   │   ├── UsernameGenerationUseCase.java
│   │   │   ├── UsernameValidationUseCase.java
│   │   │   └── CacheManagementUseCase.java
│   │   └── outbound/                # Repository interfaces (driven ports)
│   │       ├── UsernameRepository.java
│   │       ├── UsernameCacheService.java
│   │       ├── ModerationService.java
│   │       ├── UsernameGeneratorService.java
│   │       └── SafeWordsCacheService.java
│   ├── usecases/                    # 🎲 USE CASES (Business Logic)
│   │   ├── UsernameGenerationUseCaseImpl.java
│   │   ├── UsernameValidationUseCaseImpl.java
│   │   └── CacheManagementUseCaseImpl.java
│   └── exceptions/                  # Domain-specific exceptions
│       ├── UsernameGenerationException.java
│       ├── ValidationException.java
│       └── CacheException.java
├── adapters/                        # 🔌 ADAPTERS (External World)
│   ├── inbound/                     # Controllers & Resolvers
│   │   ├── rest/
│   │   │   ├── UsernameController.java
│   │   │   ├── AdminController.java
│   │   │   └── HealthController.java
│   │   ├── graphql/
│   │   │   ├── UsernameResolver.java
│   │   │   ├── QueryResolver.java
│   │   │   └── MutationResolver.java
│   │   └── dto/                     # Data Transfer Objects
│   │       ├── GenerationRequestDto.java
│   │       ├── UsernameResponseDto.java
│   │       └── ValidationResponseDto.java
│   └── outbound/                    # External System Integrations
│       ├── database/
│       │   ├── R2dbcUsernameRepository.java
│       │   ├── entity/
│       │   │   ├── UsernameEntity.java
│       │   │   └── SafeWordEntity.java
│       │   └── mapper/
│       │       └── UsernameMapper.java
│       ├── cache/
│       │   ├── ReactiveUsernameCacheService.java
│       │   ├── ReactiveSafeWordsCacheService.java
│       │   └── ReactiveBloomFilterService.java
│       ├── moderation/
│       │   ├── OpenAiModerationService.java
│       │   ├── PerspectiveModerationService.java
│       │   └── FallbackModerationService.java
│       └── generation/
│           ├── DataFakerGeneratorService.java
│           ├── strategy/
│           │   ├── GenerationStrategyFactory.java
│           │   ├── ClassicPatternStrategy.java
│           │   ├── SeparatorPatternStrategy.java
│           │   └── WordplayPatternStrategy.java
│           └── dictionary/
│               ├── EnglishWordProvider.java
│               └── SpanishWordProvider.java
├── infrastructure/                  # ⚙️ INFRASTRUCTURE
│   ├── config/
│   │   ├── DatabaseConfig.java
│   │   ├── RedisConfig.java
│   │   ├── GraphQLConfig.java
│   │   ├── SecurityConfig.java
│   │   └── WebFluxConfig.java
│   ├── monitoring/
│   │   ├── MetricsConfig.java
│   │   ├── HealthIndicators.java
│   │   └── ObservabilityConfig.java
│   └── properties/
│       ├── NameforgeProperties.java
│       └── CacheProperties.java
└── application/                     # 🚀 APPLICATION
    └── NameforgeApplication.java    # Main class
```
