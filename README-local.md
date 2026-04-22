# Z-Commerce Local Development Guide

This guide explains how to run and test the Z-Commerce application locally without AWS credentials or cloud resources.

## Quick Start (No Setup Required)

The fastest way to validate the application is to run the comprehensive test suite:

```bash
# Run all tests (unit, property-based, integration)
mvn test

# View test results - should show: Tests run: 38, Failures: 0, Errors: 0
```

This validates all business logic, data models, and service interactions using in-memory mocks.

## Local Development Options

### Option 1: Test-Only Development (Recommended for Quick Validation)

Perfect for code validation and development without any infrastructure setup.

```bash
# Run all tests
mvn test

# Run specific test types
mvn test -Dtest="*PropertyTest"     # Property-based tests only
mvn test -Dtest="*Test"             # Unit tests only  
mvn test -Dtest="*IntegrationTest"  # Integration tests only
mvn test -Dtest="*ScenariosTest"    # Scenario tests only

# Run tests with coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html  # macOS
# or navigate to target/site/jacoco/index.html in your browser
```

**What this validates:**
- All business logic and data models
- Service interactions and workflows
- Error handling and validation
- Property-based testing with randomized inputs
- Complete e-commerce workflows (mocked)

### Option 2: SAM Local API Simulation

Simulates AWS Lambda and API Gateway locally for HTTP endpoint testing.

#### Prerequisites
```bash
# Install SAM CLI
# macOS with Homebrew
brew install aws-sam-cli

# Linux/Windows - follow AWS documentation
# https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html
```

#### Setup and Run
```bash
# Build the application
mvn clean package

# Build SAM application
sam build

# Start local API Gateway (runs on http://localhost:3000)
sam local start-api --port 3000

# Test endpoints in another terminal
curl http://localhost:3000/health

# Test user registration
curl -X POST http://localhost:3000/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"Test User"}'
```

**What this provides:**
- HTTP API endpoints for testing
- Lambda function simulation
- Request/response validation
- API Gateway behavior simulation

### Option 3: Full Local Stack with DynamoDB

Complete local environment with database for end-to-end testing.

#### Prerequisites
```bash
# Option A: Docker (Recommended)
docker --version

# Option B: Java (for standalone DynamoDB Local)
java -version  # Requires Java 8+
```

#### Setup with Docker
```bash
# 1. Start DynamoDB Local
docker run -d -p 8000:8000 --name dynamodb-local amazon/dynamodb-local

# 2. Create local environment configuration
cat > local-env.json << EOF
{
  "UserServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "ProductServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000", 
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "CartServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local", 
    "ENVIRONMENT": "local"
  },
  "OrderServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "PaymentServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  }
}
EOF

# 3. Build and start application
mvn clean package
sam build
sam local start-api --env-vars local-env.json --port 3000
```

#### Setup without Docker (Standalone DynamoDB)
```bash
# 1. Download DynamoDB Local
wget https://s3.us-west-2.amazonaws.com/dynamodb-local/dynamodb_local_latest.tar.gz
tar -xzf dynamodb_local_latest.tar.gz

# 2. Start DynamoDB Local
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb -port 8000

# 3. Use local-env.json with localhost instead of host.docker.internal
# (Replace host.docker.internal with localhost in the config above)

# 4. Start application (in another terminal)
sam local start-api --env-vars local-env.json --port 3000
```

## Local Development Scripts

### Automated Setup Script

Create `local-dev-setup.sh`:

```bash
#!/bin/bash
# Z-Commerce Local Development Setup

set -e

echo "🚀 Setting up Z-Commerce for local development..."

# Check prerequisites
echo "📋 Checking prerequisites..."
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is required but not installed"
    exit 1
fi

if ! command -v sam &> /dev/null; then
    echo "⚠️  SAM CLI not found. Install with: brew install aws-sam-cli"
    echo "   Continuing with test-only setup..."
    TEST_ONLY=true
fi

# Build application
echo "🔨 Building application..."
mvn clean compile

# Run tests
echo "🧪 Running comprehensive test suite..."
mvn test

if [ "$TEST_ONLY" = true ]; then
    echo "✅ Test-only setup complete!"
    echo "   All tests passed. Application logic validated."
    exit 0
fi

# SAM setup
echo "📦 Building SAM application..."
sam build

# Check for Docker
if command -v docker &> /dev/null; then
    echo "🐳 Starting DynamoDB Local with Docker..."
    docker run -d -p 8000:8000 --name dynamodb-local amazon/dynamodb-local 2>/dev/null || \
    docker start dynamodb-local 2>/dev/null || true
    
    # Create local environment config
    cat > local-env.json << 'EOF'
{
  "UserServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "ProductServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "CartServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "OrderServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  },
  "PaymentServiceFunction": {
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000",
    "DYNAMODB_TABLE_NAME": "ZCommerceTable-local",
    "ENVIRONMENT": "local"
  }
}
EOF
    
    echo "🌐 Starting local API server..."
    echo "   API will be available at: http://localhost:3000"
    echo "   DynamoDB Local UI: http://localhost:8000/shell"
    echo ""
    echo "🎯 Test endpoints:"
    echo "   curl http://localhost:3000/health"
    echo "   curl -X POST http://localhost:3000/users/register -H 'Content-Type: application/json' -d '{\"email\":\"test@example.com\",\"password\":\"password123\",\"name\":\"Test User\"}'"
    echo ""
    
    sam local start-api --env-vars local-env.json --port 3000
else
    echo "🌐 Starting local API server (without DynamoDB)..."
    echo "   API will be available at: http://localhost:3000"
    sam local start-api --port 3000
fi
```

Make it executable:
```bash
chmod +x local-dev-setup.sh
./local-dev-setup.sh
```

### Test Script

Create `run-tests.sh`:

```bash
#!/bin/bash
# Comprehensive test runner

echo "🧪 Running Z-Commerce Test Suite"
echo "================================"

echo "📊 Running all tests..."
mvn test

echo ""
echo "📈 Generating coverage report..."
mvn jacoco:report

echo ""
echo "✅ Test Summary:"
echo "   - View detailed results in target/surefire-reports/"
echo "   - View coverage report: target/site/jacoco/index.html"

# Open coverage report if on macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "   - Opening coverage report..."
    open target/site/jacoco/index.html
fi
```

Make it executable:
```bash
chmod +x run-tests.sh
./run-tests.sh
```

## Testing Different Scenarios

### API Endpoint Testing

Once you have the local API running (`sam local start-api`):

```bash
# Health check
curl http://localhost:3000/health

# User registration
curl -X POST http://localhost:3000/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"securepass123","name":"John Doe"}'

# User login (get token)
TOKEN=$(curl -s -X POST http://localhost:3000/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"securepass123"}' | \
  jq -r '.token')

# Create product (admin operation)
curl -X POST http://localhost:3000/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Test Product","description":"A test product","price":29.99,"inventory":100}'

# List products
curl http://localhost:3000/products

# Add to cart
curl -X POST http://localhost:3000/cart/USER_ID/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":"PRODUCT_ID","quantity":2}'
```

### Property-Based Test Exploration

Run property-based tests with different configurations:

```bash
# Run with more iterations for thorough testing
mvn test -Djqwik.tries.default=1000

# Run with specific seed for reproducible results
mvn test -Djqwik.seed=42

# Run with detailed reporting
mvn test -Djqwik.reporting.onlyFailures=false
```

## Troubleshooting

### Common Issues

1. **Port already in use**:
   ```bash
   # Kill process using port 3000
   lsof -ti:3000 | xargs kill -9
   
   # Or use different port
   sam local start-api --port 3001
   ```

2. **DynamoDB Local connection issues**:
   ```bash
   # Check if DynamoDB Local is running
   curl http://localhost:8000
   
   # Restart DynamoDB Local
   docker restart dynamodb-local
   ```

3. **SAM build failures**:
   ```bash
   # Clean and rebuild
   mvn clean
   rm -rf .aws-sam
   sam build --debug
   ```

4. **Test failures**:
   ```bash
   # Run tests with debug output
   mvn test -X
   
   # Run specific failing test
   mvn test -Dtest=SpecificTestClass
   ```

### Performance Tips

- **Faster testing**: Use `mvn test -Djqwik.tries.default=10` for quicker property-based tests during development
- **Parallel testing**: Use `mvn test -T 4` to run tests in parallel
- **Skip tests during build**: Use `mvn package -DskipTests` when you just need to build

## What Each Option Validates

| Option | Business Logic | API Endpoints | Database | Full Workflow |
|--------|---------------|---------------|----------|---------------|
| Test-Only | ✅ | ❌ | ✅ (mocked) | ✅ (mocked) |
| SAM Local | ✅ | ✅ | ❌ | ⚠️ (limited) |
| Full Local | ✅ | ✅ | ✅ | ✅ |

## Next Steps

1. **Start with tests**: Run `mvn test` to validate core functionality
2. **Try SAM local**: Set up local API for endpoint testing
3. **Full stack**: Add DynamoDB Local for complete local development
4. **Deploy to AWS**: Follow the main README.md deployment guide when ready

The application is designed to work completely offline for development and testing, making it easy to validate functionality before deploying to AWS.