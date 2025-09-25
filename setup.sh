#!/bin/bash
# setup.sh - NameForge Local Environment Setup

set -e

echo "🚀 Setting up NameForge local development environment..."

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check Java 21
if ! command -v java &> /dev/null || ! java -version 2>&1 | grep -q "21"; then
    echo "❌ Java 21 is required but not installed."
    echo "Please install Java 21: https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is required but not installed."
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is required but not installed."
    echo "Please install Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✅ Prerequisites check passed!"

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env
    echo "⚠️  Please edit .env file and add your API keys:"
    echo "   - OPENAI_API_KEY (for content moderation)"
    echo "   - PERSPECTIVE_API_KEY (for content moderation)"
    echo ""
    read -p "Press Enter after configuring your API keys..."
fi

# Start services
echo "🐳 Starting Docker services..."
docker-compose up -d

# Wait for services to be ready
echo "⏳ Waiting for services to be healthy..."
sleep 30

# Check if services are running
if docker-compose ps | grep -q "Up"; then
    echo "✅ Services are running!"
    echo ""
    echo "🌐 Application URLs:"
    echo "   - API: http://localhost:8080"
    echo "   - Health: http://localhost:8081/actuator/health"
    echo "   - Management: http://localhost:8081/actuator"
    echo ""
    echo "📊 Database:"
    echo "   - PostgreSQL: localhost:5432"
    echo "   - Redis: localhost:6379"
    echo ""
    echo "🧪 Test the application:"
    echo "   curl http://localhost:8080/api/v1/health"
    echo ""
    echo "🎉 Setup complete! Happy coding!"
else
    echo "❌ Some services failed to start. Check logs:"
    echo "   docker-compose logs"
    exit 1
fi
