# NameForge Local Development Setup

## 🚀 Quick Start

### Prerequisites
- **Java 21+** - Download from [Temurin](https://adoptium.net/temurin/releases/?version=21)
- **Docker & Docker Compose** - [Install Docker](https://docs.docker.com/get-docker/)
- **Git** - For cloning the repository

### 1. Clone and Setup
```bash
git clone <repository-url>
cd nameforge_project
chmod +x setup.sh
./setup.sh
```

### 2. Configure API Keys (Optional)
Edit the `.env` file and add your API keys:
```bash
OPENAI_API_KEY=your-openai-api-key
PERSPECTIVE_API_KEY=your-perspective-api-key
```

**Note**: The application will work without these keys, but content moderation will be limited.

### 3. Start Services
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f nameforge-app

# Stop services
docker-compose down
```

## 🌐 Access Points

- **API**: http://localhost:8080
- **Health Check**: http://localhost:8081/actuator/health
- **Management**: http://localhost:8081/actuator
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

## 🧪 Testing the Application

### Health Check
```bash
curl http://localhost:8080/api/v1/health
```

### Generate Usernames
```bash
curl -X POST http://localhost:8080/api/v1/usernames/generate \
  -H "Content-Type: application/json" \
  -d '{"language": "EN", "count": 3}'
```

### Validate Username
```bash
curl http://localhost:8080/api/v1/usernames/validate/testuser123?language=EN
```

## 🔧 Development Commands

### Build Application
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Run Integration Tests
```bash
./gradlew integrationTest
```

### View Application Logs
```bash
docker-compose logs -f nameforge-app
```

### Database Access
```bash
# Connect to PostgreSQL
psql -h localhost -p 5432 -U nameforge_user -d nameforge

# Connect to Redis
redis-cli -h localhost -p 6379
```

## 🐛 Troubleshooting

### Services Not Starting
```bash
# Check service status
docker-compose ps

# View detailed logs
docker-compose logs

# Restart specific service
docker-compose restart nameforge-app
```

### Port Conflicts
If ports 8080, 8081, 5432, or 6379 are in use:
1. Stop conflicting services
2. Modify ports in `docker-compose.yml`
3. Update `.env` file accordingly

### API Keys Issues
- OpenAI API: Get key from [OpenAI Platform](https://platform.openai.com/api-keys)
- Perspective API: Get key from [Google Cloud Console](https://console.cloud.google.com/)

## 📊 Monitoring

### Application Metrics
```bash
curl http://localhost:8081/actuator/metrics
```

### Health Details
```bash
curl http://localhost:8081/actuator/health
```

### Prometheus Metrics
```bash
curl http://localhost:8081/actuator/prometheus
```

## 🛑 Cleanup

```bash
# Stop all services
docker-compose down

# Remove all data (WARNING: This will delete database and cache data)
docker-compose down -v

# Remove Docker images
docker-compose down --rmi all
```

## 📚 Next Steps

1. **Review Architecture**: Read `docs/01-Architecture-Overview.md`
2. **Examine Codebase**: Explore the hexagonal architecture in `src/`
3. **Run Tests**: Execute `./gradlew test` to verify functionality
4. **API Documentation**: Check `docs/05-API-Specifications.md`

Happy coding! 🎉
