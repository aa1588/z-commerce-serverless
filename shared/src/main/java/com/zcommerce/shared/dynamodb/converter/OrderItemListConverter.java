package com.zcommerce.shared.dynamodb.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcommerce.shared.model.OrderItem;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;

/**
 * DynamoDB attribute converter for List<OrderItem>.
 * Converts between List<OrderItem> and JSON string for DynamoDB storage.
 */
public class OrderItemListConverter implements AttributeConverter<List<OrderItem>> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<OrderItem>> TYPE_REFERENCE = new TypeReference<List<OrderItem>>() {};

    @Override
    public AttributeValue transformFrom(List<OrderItem> input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        
        try {
            String json = objectMapper.writeValueAsString(input);
            return AttributeValue.builder().s(json).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderItem list", e);
        }
    }

    @Override
    public List<OrderItem> transformTo(AttributeValue input) {
        if (input == null || input.nul() != null && input.nul()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(input.s(), TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize OrderItem list", e);
        }
    }

    @Override
    public EnhancedType<List<OrderItem>> type() {
        return EnhancedType.listOf(OrderItem.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}