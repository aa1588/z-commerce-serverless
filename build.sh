#!/bin/bash

# Z-Commerce Build Script
# Builds all modules and packages Lambda functions for deployment

set -e

echo "Building Z-Commerce Serverless E-commerce Application..."

# Clean and compile all modules
echo "Cleaning and compiling all modules..."
mvn clean compile

# Run tests
echo "Running tests..."
mvn test

# Package Lambda functions
echo "Packaging Lambda functions..."
mvn package

# Build SAM application
echo "Building SAM application..."
sam build

echo "Build completed successfully!"
echo ""
echo "Next steps:"
echo "1. Deploy with: sam deploy --guided"
echo "2. Test locally with: sam local start-api"
echo "3. Run specific tests with: mvn test -Dtest=ClassName"