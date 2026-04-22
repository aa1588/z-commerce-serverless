# Z-Commerce Serverless E-commerce Application

A serverless e-commerce platform built with AWS Lambda, Java 17, API Gateway, and DynamoDB. This project demonstrates modern serverless architecture patterns, single-table DynamoDB design, and comprehensive testing strategies including property-based testing.

## Architecture Overview

Z-Commerce follows a microservices architecture with each Lambda function handling a specific business domain:

- **User Service**: User registration, authentication, and profile management
- **Product Service**: Product catalog management and inventory
- **Cart Service**: Shopping cart operations and item management
- **Order Service**: Order processing and workflow management
- **Payment Service**: Payment processing (mock implementation)

## Project Structure

```
z-commerce/
├── shared/                     # Shared utilities and infrastructure
│   ├── src/main/java/com/zcommerce/shared/
│   │   ├── api/               # API response utilities and base handler
│   │   ├── dynamodb/          # DynamoDB configuration and key builders
│   │   ├── exception/         # Custom exception hierarchy
│   │   ├── logging/           # Structured logging utilities
│   │   └── util/              # Validation and utility classes
│   └── pom.xml
├── user-service/              # User management Lambda
├── product-service/           # Product catalog Lambda
├── cart-service/              # Shopping cart Lambda
├── order-service/             # Order processing Lambda
├── payment-service/           # Payment processing Lambda
├── template.yaml              # SAM template for AWS infrastructure
├── pom.xml                    # Parent Maven configuration
└── README.md
```

## Technology Stack

- **Runtime**: Java 17
- **Framework**: AWS Lambda with SAM (Serverless Application Model)
- **Database**: DynamoDB with single-table design
- **API**: API Gateway HTTP API
- **Build Tool**: Maven
- **Testing**: JUnit 5 + jqwik (property-based testing)
- **Logging**: Structured JSON logging compatible with CloudWatch

## Key Features

### Single-Table DynamoDB Design
- Efficient data access patterns using partition and sort keys
- Global Secondary Index (GSI) for alternative query patterns
- Optimized for serverless workloads with minimal cold start impact

### Comprehensive Error Handling
- Custom exception hierarchy with structured error information
- Centralized error handling with appropriate HTTP status codes
- Detailed logging for debugging and monitoring

### Structured Logging
- JSON-formatted logs compatible with AWS CloudWatch
- Request correlation IDs for tracing
- Contextual information for debugging

### Property-Based Testing
- Universal correctness properties validated across all inputs
- Complementary to unit tests for comprehensive coverage
- Uses jqwik library for robust randomized testing

## Prerequisites

- Java 17 or later
- Maven 3.8 or later
- AWS CLI configured with appropriate permissions
- SAM CLI for local development and deployment

## Getting Started

### 1. Build the Project

```bash
mvn clean compile
```

### 2. Run Tests

```bash
mvn test
```

### 3. Package for Deployment

```bash
mvn clean package
```

### 4. Deploy with SAM

```bash
# Build SAM application
sam build

# Deploy to AWS
sam deploy --guided
```

### 5. Local Development

```bash
# Start local API Gateway
sam local start-api

# Invoke specific function locally
sam local invoke UserServiceFunction --event events/user-register.json
```

## Running Tests

Z-Commerce includes a comprehensive test suite with unit tests, property-based tests, and integration tests. Here's how to run different types of tests:

### Prerequisites for Testing

Ensure you have the following installed:
- Java 17 or later
- Maven 3.8 or later

### Running All Tests

To run the complete test suite across all services:

```bash
# Run all tests with Maven
mvn test

# Run tests with verbose output
mvn test -X

# Run tests and generate coverage reports
mvn test jacoco:report
```

### Running Tests for Specific Services

To run tests for individual services:

```bash
# User Service tests
mvn test -pl user-service

# Product Service tests
mvn test -pl product-service

# Cart Service tests
mvn test -pl cart-service

# Order Service tests
mvn test -pl order-service

# Payment Service tests
mvn test -pl payment-service

# Shared library tests
mvn test -pl shared
```

### Running Specific Test Types

#### Unit Tests Only
```bash
# Run only unit tests (excludes property-based tests)
mvn test -Dtest="*Test" -DfailIfNoTests=false
```

#### Property-Based Tests Only
```bash
# Run only property-based tests
mvn test -Dtest="*PropertyTest" -DfailIfNoTests=false
```

#### Integration Tests Only
```bash
# Run only integration tests
mvn test -Dtest="*IntegrationTest" -DfailIfNoTests=false
```

### Running Individual Test Classes

```bash
# Run a specific test class
mvn test -Dtest=PaymentScenariosTest

# Run a specific test method
mvn test -Dtest=PaymentScenariosTest#processPayment_WithValidCreditCard_ProcessesSuccessfully

# Run multiple test classes
mvn test -Dtest=PaymentScenariosTest,OrderScenariosTest
```

### Test Configuration Options

#### Property-Based Test Configuration
Property-based tests use jqwik and can be configured with system properties:

```bash
# Increase number of test iterations for property tests
mvn test -Djqwik.tries.default=1000

# Set random seed for reproducible test runs
mvn test -Djqwik.seed=42

# Enable detailed reporting
mvn test -Djqwik.reporting.onlyFailures=false
```

#### Test Output and Reporting
```bash
# Generate detailed test reports
mvn test surefire-report:report

# Run tests with coverage and generate HTML report
mvn clean test jacoco:report

# Skip tests during build (not recommended)
mvn clean package -DskipTests
```

### Continuous Testing During Development

For active development, you can run tests continuously:

```bash
# Watch for changes and re-run tests (requires Maven wrapper)
./mvnw test -Dtest.continuous=true

# Or use Maven with file watching (if available)
mvn test -Dtest.watch=true
```

### Test Troubleshooting

#### Common Issues and Solutions

1. **OutOfMemoryError during tests**:
   ```bash
   export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
   mvn test
   ```

2. **Tests failing due to timing issues**:
   ```bash
   # Run tests with increased timeout
   mvn test -Dsurefire.timeout=300
   ```

3. **Property-based tests taking too long**:
   ```bash
   # Reduce iterations for faster feedback
   mvn test -Djqwik.tries.default=50
   ```

4. **Debugging test failures**:
   ```bash
   # Run with debug output
   mvn test -Dmaven.surefire.debug=true
   
   # Run single test with full stack traces
   mvn test -Dtest=FailingTest -Dsurefire.printSummary=true
   ```

## Deployment Guide

This section provides comprehensive instructions for deploying Z-Commerce to AWS using SAM (Serverless Application Model).

### Prerequisites for Deployment

Before deploying, ensure you have:

1. **AWS CLI installed and configured**:
   ```bash
   # Install AWS CLI (if not already installed)
   curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
   unzip awscliv2.zip
   sudo ./aws/install
   
   # Configure AWS credentials
   aws configure
   ```

2. **SAM CLI installed**:
   ```bash
   # Install SAM CLI (macOS with Homebrew)
   brew install aws-sam-cli
   
   # Install SAM CLI (Linux/Windows - see AWS documentation)
   # https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html
   ```

3. **Required AWS Permissions**:
   Your AWS user/role needs permissions for:
   - CloudFormation (create/update/delete stacks)
   - Lambda (create/update functions)
   - API Gateway (create/manage APIs)
   - DynamoDB (create/manage tables)
   - IAM (create/manage roles and policies)
   - S3 (for deployment artifacts)

### Step-by-Step Deployment

#### Step 1: Prepare the Application

```bash
# Clone the repository (if not already done)
git clone <repository-url>
cd z-commerce

# Build the application
mvn clean package

# Verify all tests pass
mvn test
```

#### Step 2: Configure SAM Parameters

Create a `samconfig.toml` file for your deployment configuration:

```bash
# Create samconfig.toml for development environment
cat > samconfig.toml << EOF
version = 0.1
[default]
[default.deploy]
[default.deploy.parameters]
stack_name = "z-commerce-dev"
s3_bucket = "your-sam-deployment-bucket"
s3_prefix = "z-commerce"
region = "us-east-1"
capabilities = "CAPABILITY_IAM"
parameter_overrides = "Environment=dev TableName=ZCommerceTable-dev"
confirm_changeset = true
EOF
```

#### Step 3: Build the SAM Application

```bash
# Build the application
sam build

# Validate the SAM template
sam validate

# Check build output
ls -la .aws-sam/build/
```

#### Step 4: Deploy to AWS

##### First-Time Deployment (Guided)

```bash
# Deploy with guided prompts (recommended for first deployment)
sam deploy --guided

# Follow the prompts:
# - Stack Name: z-commerce-dev
# - AWS Region: us-east-1
# - Parameter Environment: dev
# - Parameter TableName: ZCommerceTable-dev
# - Confirm changes before deploy: Y
# - Allow SAM CLI IAM role creation: Y
# - Save parameters to samconfig.toml: Y
```

##### Subsequent Deployments

```bash
# Deploy using saved configuration
sam deploy

# Deploy with specific parameters
sam deploy --parameter-overrides Environment=staging TableName=ZCommerceTable-staging

# Deploy without confirmation prompts
sam deploy --no-confirm-changeset
```

#### Step 5: Verify Deployment

```bash
# Get stack outputs (API Gateway URL, etc.)
aws cloudformation describe-stacks --stack-name z-commerce-dev --query 'Stacks[0].Outputs'

# Test API endpoints
API_URL=$(aws cloudformation describe-stacks --stack-name z-commerce-dev --query 'Stacks[0].Outputs[?OutputKey==`ApiGatewayUrl`].OutputValue' --output text)

# Test health endpoint
curl $API_URL/health

# Test user registration
curl -X POST $API_URL/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"Test User"}'
```

### Environment-Specific Deployments

#### Development Environment
```bash
# Deploy to development
sam deploy --parameter-overrides Environment=dev TableName=ZCommerceTable-dev --stack-name z-commerce-dev
```

#### Staging Environment
```bash
# Deploy to staging
sam deploy --parameter-overrides Environment=staging TableName=ZCommerceTable-staging --stack-name z-commerce-staging
```

#### Production Environment
```bash
# Deploy to production (with additional safeguards)
sam deploy --parameter-overrides Environment=prod TableName=ZCommerceTable-prod --stack-name z-commerce-prod --no-confirm-changeset false
```

### Advanced Deployment Options

#### Blue/Green Deployment
```bash
# Enable gradual deployment for Lambda functions
sam deploy --parameter-overrides DeploymentPreference=Canary10Percent5Minutes
```

#### Custom Domain Setup
```bash
# Deploy with custom domain (requires Route53 hosted zone)
sam deploy --parameter-overrides \
  DomainName=api.yourcompany.com \
  CertificateArn=arn:aws:acm:us-east-1:123456789012:certificate/your-cert-id
```

#### VPC Configuration
```bash
# Deploy Lambda functions in VPC (for database access)
sam deploy --parameter-overrides \
  VpcId=vpc-12345678 \
  SubnetIds=subnet-12345678,subnet-87654321 \
  SecurityGroupIds=sg-12345678
```

### Monitoring and Maintenance

#### View Logs
```bash
# View logs for specific function
sam logs -n UserServiceFunction --stack-name z-commerce-dev --tail

# View logs with filter
sam logs -n UserServiceFunction --stack-name z-commerce-dev --filter "ERROR"

# View logs for specific time range
sam logs -n UserServiceFunction --stack-name z-commerce-dev --start-time 2024-01-01T00:00:00 --end-time 2024-01-01T23:59:59
```

#### Update Application
```bash
# Make code changes, then redeploy
mvn clean package
sam build
sam deploy

# Deploy only changed functions
sam deploy --force-upload
```

#### Rollback Deployment
```bash
# Rollback to previous version
aws cloudformation cancel-update-stack --stack-name z-commerce-dev

# Or delete and redeploy previous version
aws cloudformation delete-stack --stack-name z-commerce-dev
# Then redeploy previous version
```

### Cleanup and Teardown

#### Delete Stack
```bash
# Delete the entire stack
sam delete --stack-name z-commerce-dev

# Delete with confirmation
aws cloudformation delete-stack --stack-name z-commerce-dev

# Monitor deletion progress
aws cloudformation describe-stacks --stack-name z-commerce-dev
```

#### Clean Up S3 Artifacts
```bash
# Remove deployment artifacts from S3
aws s3 rm s3://your-sam-deployment-bucket/z-commerce --recursive
```

### Troubleshooting Deployment Issues

#### Common Deployment Problems

1. **Insufficient Permissions**:
   ```bash
   # Check your AWS credentials and permissions
   aws sts get-caller-identity
   aws iam get-user
   ```

2. **Stack Already Exists**:
   ```bash
   # Use a different stack name or delete existing stack
   sam deploy --stack-name z-commerce-dev-v2
   ```

3. **Build Failures**:
   ```bash
   # Clean and rebuild
   mvn clean
   rm -rf .aws-sam
   sam build --debug
   ```

4. **Template Validation Errors**:
   ```bash
   # Validate template syntax
   sam validate --debug
   aws cloudformation validate-template --template-body file://template.yaml
   ```

5. **Lambda Function Errors**:
   ```bash
   # Check function logs
   sam logs -n FunctionName --stack-name z-commerce-dev --tail
   
   # Test function locally
   sam local invoke FunctionName --event events/test-event.json
   ```

### CI/CD Pipeline Setup

For automated deployments, create a CI/CD pipeline:

#### GitHub Actions Example
```yaml
# .github/workflows/deploy.yml
name: Deploy Z-Commerce
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Build and Test
        run: |
          mvn clean test
          mvn package
      - name: Deploy to AWS
        run: |
          sam build
          sam deploy --no-confirm-changeset --no-fail-on-empty-changeset
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
```

This comprehensive guide covers all aspects of testing and deployment for the Z-Commerce application, from basic test execution to production deployment strategies.

## API Endpoints

### User Management
- `POST /users/register` - Register new user
- `POST /users/login` - User authentication
- `GET /users/{userId}` - Get user profile
- `PUT /users/{userId}` - Update user profile

### Product Catalog
- `GET /products` - List all products
- `GET /products/{productId}` - Get product details
- `POST /products` - Create product (admin)
- `PUT /products/{productId}` - Update product (admin)
- `DELETE /products/{productId}` - Delete product (admin)

### Shopping Cart
- `GET /cart/{userId}` - Get user's cart
- `POST /cart/{userId}/items` - Add item to cart
- `PUT /cart/{userId}/items/{productId}` - Update item quantity
- `DELETE /cart/{userId}/items/{productId}` - Remove item from cart

### Order Processing
- `POST /orders` - Create order from cart
- `GET /orders/{orderId}` - Get order details
- `GET /users/{userId}/orders` - Get user's orders

### Payment Processing
- `POST /payments/process` - Process payment
- `GET /payments/{transactionId}` - Get payment status

## Configuration

### Environment Variables

- `DYNAMODB_TABLE_NAME`: DynamoDB table name (default: ZCommerceTable)
- `AWS_REGION`: AWS region (default: us-east-1)
- `ENVIRONMENT`: Deployment environment (dev/staging/prod)

### SAM Parameters

- `Environment`: Deployment environment
- `TableName`: DynamoDB table name prefix

## Testing Strategy

### Unit Tests
- Specific examples and edge cases
- Integration points between components
- Error handling scenarios

### Property-Based Tests
- Universal correctness properties
- Randomized input validation
- Business rule verification across all valid inputs

### Integration Tests
- End-to-end API workflows
- Cross-service communication
- Data consistency validation

## Monitoring and Logging

### CloudWatch Integration
- Structured JSON logs for easy parsing
- Request correlation for distributed tracing
- Error metrics and alerting

### X-Ray Tracing
- Distributed request tracing across services
- Performance monitoring and bottleneck identification
- Service map visualization

## Security Considerations

- JWT-based authentication
- Password hashing with BCrypt
- Input validation and sanitization
- Least-privilege IAM permissions
- CORS configuration for web clients

## Development Guidelines

### Code Style
- Follow Java naming conventions
- Use meaningful variable and method names
- Include comprehensive JavaDoc comments
- Maintain consistent error handling patterns

### Testing Requirements
- Write both unit and property-based tests
- Achieve minimum 80% code coverage
- Test error scenarios and edge cases
- Validate business rules comprehensively

### Deployment Process
- Use SAM for infrastructure as code
- Implement CI/CD pipelines
- Environment-specific configurations
- Automated testing before deployment

## Contributing

1. Follow the established project structure
2. Write comprehensive tests for new features
3. Update documentation for API changes
4. Follow the coding standards and conventions
5. Ensure all tests pass before submitting changes

## License

This project is intended for educational purposes and demonstrates AWS Lambda development patterns and serverless architecture best practices.