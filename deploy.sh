#!/bin/bash

# Z-Commerce Deployment Script
# Deploys the serverless e-commerce application to AWS

set -e

echo "Deploying Z-Commerce Serverless E-commerce Application..."

# Check if SAM CLI is installed
if ! command -v sam &> /dev/null; then
    echo "Error: SAM CLI is not installed. Please install it first:"
    echo "https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html"
    exit 1
fi

# Check if AWS CLI is configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo "Error: AWS CLI is not configured. Please run 'aws configure' first."
    exit 1
fi

# Build the application
echo "Building application..."
./build.sh

# Deploy with SAM
echo "Deploying to AWS..."
if [ "$1" = "--guided" ]; then
    sam deploy --guided
else
    sam deploy
fi

echo "Deployment completed successfully!"
echo ""
echo "To test the deployment:"
echo "1. Check the CloudFormation stack in AWS Console"
echo "2. Test API endpoints using the output URL"
echo "3. Monitor logs in CloudWatch"