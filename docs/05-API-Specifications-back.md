# NameForge Backend - Especificaciones de APIs

## 📋 Información del Documento

**Proyecto**: NameForge - API Layer Specifications
**APIs**: REST + GraphQL
**Tecnología**: Spring WebFlux + Spring GraphQL
**Fecha**: 2024-09-24

## 🌐 REST API Specification

### Base Configuration
```yaml
Base URL: https://api.nameforge.com/api/v1
Content-Type: application/json
Accept: application/json
Rate Limit: 50 requests per second per IP
Timeout: 30 seconds
```

### Authentication & Headers
```http
# Request Headers
Content-Type: application/json
Accept: application/json
User-Agent: NameForge-Client/1.0.0
X-Request-ID: uuid-v4  # For tracing
Authorization: Bearer <token>  # Future implementation

# Response Headers
Content-Type: application/json
X-Request-ID: uuid-v4
X-Response-Time: 45ms
X-Rate-Limit-Remaining: 49
X-Rate-Limit-Reset: 1640995200
```

### Core Endpoints

#### 1. Generate Usernames
```http
POST /api/v1/usernames/generate
```

**Request Body:**
```json
{
  "language": "EN",
  "count": 5
}
```

**Response (200 OK):**
```json
{
  "usernames": [
    "cleverpanda42",
    "brightstar789",
    "swifteagle23",
    "coolwolf156",
    "smartfox91"
  ],
  "generatedAt": "2024-09-24T10:30:00Z",
  "language": "EN",
  "totalGenerated": 5,
  "cacheHit": true,
  "responseTimeMs": 45
}
```

**Error Responses:**
```json
// 400 Bad Request - Invalid input
{
  "error": "INVALID_REQUEST",
  "message": "Count must be between 1 and 10",
  "timestamp": "2024-09-24T10:30:00Z",
  "path": "/api/v1/usernames/generate"
}

// 429 Too Many Requests
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit of 50 requests per second exceeded",
  "retryAfter": 1,
  "timestamp": "2024-09-24T10:30:00Z"
}

// 503 Service Unavailable
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Username generation service temporarily unavailable",
  "timestamp": "2024-09-24T10:30:00Z"
}
```

#### 2. Validate Username
```http
GET /api/v1/usernames/validate/{username}?language=EN
```

**Path Parameters:**
- `username` (required): Username to validate (5-30 characters, [a-z0-9_-])

**Query Parameters:**
- `language` (optional, default: EN): Language for content moderation

**Response (200 OK):**
```json
{
  "username": "cleverpanda42",
  "isValid": true,
  "isUnique": true,
  "isAppropriate": true,
  "isValidFormat": true,
  "reasons": [],
  "confidenceScore": 0.98,
  "validatedAt": "2024-09-24T10:30:00Z"
}
```

**Invalid Response:**
```json
{
  "username": "badword123",
  "isValid": false,
  "isUnique": true,
  "isAppropriate": false,
  "isValidFormat": true,
  "reasons": [
    "Content not appropriate for general use"
  ],
  "confidenceScore": 0.85,
  "validatedAt": "2024-09-24T10:30:00Z"
}
```

#### 3. Mark Username as Used
```http
POST /api/v1/usernames/mark-used/{username}
```

**Response (200 OK):**
```json
{
  "username": "cleverpanda42",
  "markedAsUsed": true,
  "timestamp": "2024-09-24T10:30:00Z"
}
```

#### 4. Batch Generation (Admin)
```http
POST /api/v1/usernames/batch-generate
Authorization: Bearer <admin-token>
```

**Request Body:**
```json
{
  "language": "EN",
  "batchSize": 10000,
  "patterns": ["CLASSIC", "SEPARATOR", "WORDPLAY"],
  "categories": ["animals", "colors", "nature"]
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "batch-gen-uuid-123",
  "status": "QUEUED",
  "estimatedCompletionTime": "2024-09-24T10:35:00Z",
  "statusUrl": "/api/v1/jobs/batch-gen-uuid-123"
}
```

#### 5. Health Check
```http
GET /api/v1/health
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "timestamp": "2024-09-24T10:30:00Z",
  "components": {
    "database": {
      "status": "UP",
      "responseTime": "15ms"
    },
    "redis": {
      "status": "UP",
      "responseTime": "5ms"
    },
    "moderationApi": {
      "status": "UP",
      "responseTime": "120ms"
    }
  },
  "metrics": {
    "totalGenerated": 1250000,
    "cacheHitRate": 0.95,
    "averageResponseTime": "45ms"
  }
}
```

### OpenAPI 3.0 Specification
```yaml
openapi: 3.0.3
info:
  title: NameForge API
  description: Username generation and validation service
  version: 1.0.0
  contact:
    name: NameForge API Support
    email: api-support@nameforge.com

servers:
  - url: https://api.nameforge.com/api/v1
    description: Production server
  - url: https://staging-api.nameforge.com/api/v1
    description: Staging server

paths:
  /usernames/generate:
    post:
      summary: Generate usernames
      operationId: generateUsernames
      tags:
        - Usernames
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/GenerationRequest'
            examples:
              basic:
                summary: Basic generation request
                value:
                  language: "EN"
                  count: 3
              spanish:
                summary: Spanish usernames
                value:
                  language: "ES"
                  count: 5
      responses:
        '200':
          description: Successfully generated usernames
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UsernameResponse'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '429':
          description: Rate limit exceeded
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RateLimitError'

  /usernames/validate/{username}:
    get:
      summary: Validate username
      operationId: validateUsername
      tags:
        - Usernames
      parameters:
        - name: username
          in: path
          required: true
          schema:
            type: string
            pattern: '^[a-z0-9_-]{5,30}$'
        - name: language
          in: query
          schema:
            $ref: '#/components/schemas/Language'
            default: EN
      responses:
        '200':
          description: Validation result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationResponse'

components:
  schemas:
    Language:
      type: string
      enum: [EN, ES]
      description: Supported languages

    GenerationRequest:
      type: object
      required:
        - language
      properties:
        language:
          $ref: '#/components/schemas/Language'
        count:
          type: integer
          minimum: 1
          maximum: 10
          default: 1

    UsernameResponse:
      type: object
      properties:
        usernames:
          type: array
          items:
            type: string
        generatedAt:
          type: string
          format: date-time
        language:
          $ref: '#/components/schemas/Language'
        totalGenerated:
          type: integer
        cacheHit:
          type: boolean
        responseTimeMs:
          type: integer

    ValidationResponse:
      type: object
      properties:
        username:
          type: string
        isValid:
          type: boolean
        isUnique:
          type: boolean
        isAppropriate:
          type: boolean
        isValidFormat:
          type: boolean
        reasons:
          type: array
          items:
            type: string
        confidenceScore:
          type: number
          format: double
        validatedAt:
          type: string
          format: date-time

    ErrorResponse:
      type: object
      properties:
        error:
          type: string
        message:
          type: string
        timestamp:
          type: string
          format: date-time
        path:
          type: string
```

## 🔗 GraphQL API Specification

### Schema Definition
```graphql
# Main GraphQL Schema
schema {
    query: Query
    mutation: Mutation
    subscription: Subscription
}

type Query {
    """
    Generate usernames based on specified criteria
    """
    generateUsernames(input: GenerationInput!): UsernameResponse!

    """
    Validate a specific username
    """
    validateUsername(username: String!, language: Language = EN): ValidationResponse!

    """
    Get username generation statistics
    """
    generationStats(language: Language, dateRange: DateRangeInput): GenerationStatsResponse!

    """
    Health check for the service
    """
    health: HealthResponse!
}

type Mutation {
    """
    Mark a username as used
    """
    markUsernameAsUsed(username: String!): Boolean!

    """
    Clear cache for specified language
    """
    clearCache(language: Language!): Boolean!

    """
    Initiate batch generation job
    """
    startBatchGeneration(input: BatchGenerationInput!): BatchJobResponse!
}

type Subscription {
    """
    Real-time generation statistics
    """
    generationMetrics(language: Language): GenerationMetrics!

    """
    Batch job status updates
    """
    batchJobStatus(jobId: ID!): BatchJobStatus!
}

# Input Types
input GenerationInput {
    language: Language!
    count: Int! = 1
    preferredPattern: PatternType
    category: String
}

input BatchGenerationInput {
    language: Language!
    batchSize: Int!
    patterns: [PatternType!]
    categories: [String!]
}

input DateRangeInput {
    startDate: Date!
    endDate: Date!
}

# Response Types
type UsernameResponse {
    usernames: [String!]!
    generatedAt: DateTime!
    language: Language!
    totalGenerated: Int!
    cacheHit: Boolean!
    responseTime: Int!
    metadata: GenerationMetadata
}

type ValidationResponse {
    username: String!
    isValid: Boolean!
    isUnique: Boolean!
    isAppropriate: Boolean!
    isValidFormat: Boolean!
    reasons: [String!]!
    confidenceScore: Float!
    validatedAt: DateTime!
}

type GenerationStatsResponse {
    language: Language!
    totalGenerated: Long!
    totalServed: Long!
    cacheHitRate: Float!
    averageResponseTime: Float!
    dateRange: DateRange!
    patterns: [PatternStats!]!
}

type GenerationMetadata {
    patterns: [PatternType!]!
    categories: [String!]!
    averageLength: Float!
    uniqueWords: Int!
}

type BatchJobResponse {
    jobId: ID!
    status: JobStatus!
    estimatedCompletion: DateTime
    statusUrl: String!
}

type HealthResponse {
    status: ServiceStatus!
    timestamp: DateTime!
    components: [ComponentHealth!]!
    metrics: ServiceMetrics!
}

type ComponentHealth {
    name: String!
    status: ServiceStatus!
    responseTime: Int
    details: String
}

type ServiceMetrics {
    totalGenerated: Long!
    cacheHitRate: Float!
    averageResponseTime: Float!
    activeConnections: Int!
}

# Enums
enum Language {
    EN
    ES
}

enum PatternType {
    CLASSIC
    SEPARATOR
    WORDPLAY
}

enum ServiceStatus {
    UP
    DOWN
    DEGRADED
    MAINTENANCE
}

enum JobStatus {
    QUEUED
    RUNNING
    COMPLETED
    FAILED
    CANCELLED
}

# Scalars
scalar DateTime
scalar Date
scalar Long
```

### GraphQL Queries Examples

#### Basic Username Generation
```graphql
query GenerateUsernames($input: GenerationInput!) {
  generateUsernames(input: $input) {
    usernames
    generatedAt
    language
    totalGenerated
    cacheHit
    responseTime
    metadata {
      patterns
      categories
      averageLength
    }
  }
}

# Variables
{
  "input": {
    "language": "EN",
    "count": 5
  }
}
```

#### Username Validation
```graphql
query ValidateUsername($username: String!, $language: Language!) {
  validateUsername(username: $username, language: $language) {
    username
    isValid
    isUnique
    isAppropriate
    isValidFormat
    reasons
    confidenceScore
    validatedAt
  }
}

# Variables
{
  "username": "cleverpanda42",
  "language": "EN"
}
```

#### Generation Statistics
```graphql
query GenerationStats($language: Language, $dateRange: DateRangeInput) {
  generationStats(language: $language, dateRange: $dateRange) {
    language
    totalGenerated
    totalServed
    cacheHitRate
    averageResponseTime
    patterns {
      type
      count
      percentage
    }
  }
}
```

#### Batch Generation Mutation
```graphql
mutation StartBatchGeneration($input: BatchGenerationInput!) {
  startBatchGeneration(input: $input) {
    jobId
    status
    estimatedCompletion
    statusUrl
  }
}

# Variables
{
  "input": {
    "language": "EN",
    "batchSize": 10000,
    "patterns": ["CLASSIC", "SEPARATOR"],
    "categories": ["animals", "colors"]
  }
}
```

#### Real-time Subscription
```graphql
subscription GenerationMetrics($language: Language!) {
  generationMetrics(language: $language) {
    timestamp
    language
    requestsPerSecond
    cacheHitRate
    averageResponseTime
    activeRequests
  }
}
```

## 📊 Error Handling

### REST Error Codes
```yaml
400 Bad Request:
  - INVALID_REQUEST: Malformed request body
  - INVALID_LANGUAGE: Unsupported language code
  - INVALID_COUNT: Count outside allowed range (1-10)
  - INVALID_USERNAME_FORMAT: Username format validation failed

401 Unauthorized:
  - MISSING_TOKEN: Authorization header missing
  - INVALID_TOKEN: Token expired or malformed

403 Forbidden:
  - INSUFFICIENT_PERMISSIONS: Admin endpoints require admin role
  - FEATURE_DISABLED: Feature temporarily disabled

429 Too Many Requests:
  - RATE_LIMIT_EXCEEDED: Per-IP rate limit exceeded
  - QUOTA_EXCEEDED: Daily quota exceeded

500 Internal Server Error:
  - SERVICE_ERROR: Internal service error
  - DATABASE_ERROR: Database connection failed
  - CACHE_ERROR: Redis connection failed

503 Service Unavailable:
  - SERVICE_MAINTENANCE: Service under maintenance
  - CIRCUIT_BREAKER_OPEN: External service unavailable
```

### GraphQL Error Types
```graphql
enum ErrorCode {
    INVALID_INPUT
    UNAUTHORIZED
    FORBIDDEN
    RATE_LIMITED
    SERVICE_UNAVAILABLE
    INTERNAL_ERROR
}

type Error {
    code: ErrorCode!
    message: String!
    field: String
    timestamp: DateTime!
}
```

### Error Response Format
```json
{
  "error": "INVALID_REQUEST",
  "message": "Username count must be between 1 and 10",
  "timestamp": "2024-09-24T10:30:00Z",
  "path": "/api/v1/usernames/generate",
  "requestId": "req-uuid-123",
  "details": {
    "field": "count",
    "providedValue": 15,
    "allowedRange": "1-10"
  }
}
```

## 🔒 Security Specifications

### Rate Limiting
```yaml
Global Limits:
  - 50 requests per second per IP
  - 1000 requests per minute per IP
  - 10000 requests per hour per IP

Endpoint-Specific Limits:
  /generate:
    - 10 requests per second
    - 100 requests per minute

  /validate:
    - 20 requests per second
    - 200 requests per minute

  /batch-generate:
    - 1 request per minute (admin only)
```

### Input Validation
```yaml
Username Validation:
  - Length: 5-30 characters
  - Characters: [a-z0-9_-] only
  - No consecutive special chars: __, --, _-, -_
  - Must start and end with alphanumeric

Request Validation:
  - Content-Type: application/json required
  - Request body size: Max 1KB
  - Timeout: 30 seconds
  - Required fields validated
```

### Response Headers Security
```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
```

## 📈 Performance Specifications

### SLA Targets
```yaml
Availability: 99.9% uptime
Response Times:
  - P50: < 50ms
  - P95: < 200ms
  - P99: < 500ms

Throughput:
  - 200+ requests/second sustained
  - 500+ requests/second peak (1 minute)

Cache Performance:
  - Hit ratio: > 95%
  - Cache response: < 10ms
  - Miss response: < 100ms
```

### Monitoring Endpoints
```yaml
/api/v1/metrics:
  - Prometheus format metrics
  - Request rates, response times
  - Error rates by endpoint
  - Cache statistics

/api/v1/health:
  - Component health status
  - Database connectivity
  - Redis availability
  - External API status

/api/v1/info:
  - Application version
  - Build information
  - Configuration status
```

---
**Documento generado**: 2024-09-24
**Autor**: API Development Team
**Versión**: 1.0.0