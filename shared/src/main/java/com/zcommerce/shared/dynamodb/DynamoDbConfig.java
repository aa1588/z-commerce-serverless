package com.zcommerce.shared.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Configuration class for DynamoDB clients.
 * Provides singleton instances of DynamoDB clients for the application.
 */
public class DynamoDbConfig {
    private static final String TABLE_NAME = System.getenv().getOrDefault("DYNAMODB_TABLE_NAME", "ZCommerceTable");
    private static final Region AWS_REGION = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));
    
    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbEnhancedClient enhancedClient;

    public static synchronized DynamoDbClient getDynamoDbClient() {
        if (dynamoDbClient == null) {
            dynamoDbClient = DynamoDbClient.builder()
                    .region(AWS_REGION)
                    .build();
        }
        return dynamoDbClient;
    }

    public static synchronized DynamoDbEnhancedClient getEnhancedClient() {
        if (enhancedClient == null) {
            enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(getDynamoDbClient())
                    .build();
        }
        return enhancedClient;
    }

    public static String getTableName() {
        return TABLE_NAME;
    }

    public static Region getRegion() {
        return AWS_REGION;
    }
}