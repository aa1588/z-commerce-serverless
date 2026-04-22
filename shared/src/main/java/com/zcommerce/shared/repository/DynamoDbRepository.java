package com.zcommerce.shared.repository;

import com.zcommerce.shared.dynamodb.DynamoDbConfig;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.logging.StructuredLogger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base DynamoDB repository implementation providing common CRUD operations.
 * 
 * @param <T> Entity type
 */
public abstract class DynamoDbRepository<T> implements Repository<T, String> {
    
    protected final DynamoDbEnhancedClient enhancedClient;
    protected final DynamoDbTable<T> table;
    protected final StructuredLogger logger;
    protected final String entityType;

    protected DynamoDbRepository(Class<T> entityClass, String entityType) {
        this.enhancedClient = DynamoDbConfig.getEnhancedClient();
        this.table = enhancedClient.table(DynamoDbConfig.getTableName(), TableSchema.fromBean(entityClass));
        this.logger = new StructuredLogger("DynamoDbRepository");
        this.entityType = entityType;
    }

    @Override
    public T save(T entity) {
        try {
            logger.info("entity_save_start", Map.of(
                "entityType", entityType,
                "entity", entity.toString()
            ));
            
            table.putItem(entity);
            
            logger.info("entity_save_success", Map.of(
                "entityType", entityType
            ));
            
            return entity;
        } catch (DynamoDbException e) {
            logger.error("entity_save_error", Map.of(
                "entityType", entityType,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to save " + entityType, e);
        }
    }

    @Override
    public Optional<T> findById(String id) {
        try {
            Key key = buildKey(id);
            T item = table.getItem(key);
            
            logger.info("entity_find_by_id", Map.of(
                "entityType", entityType,
                "id", id,
                "found", item != null
            ));
            
            return Optional.ofNullable(item);
        } catch (DynamoDbException e) {
            logger.error("entity_find_error", Map.of(
                "entityType", entityType,
                "id", id,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find " + entityType + " by id: " + id, e);
        }
    }

    @Override
    public List<T> findAll() {
        try {
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(buildEntityTypeFilter())
                .build();
                
            List<T> items = table.scan(scanRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
            
            logger.info("entity_find_all", Map.of(
                "entityType", entityType,
                "count", items.size()
            ));
            
            return items;
        } catch (DynamoDbException e) {
            logger.error("entity_find_all_error", Map.of(
                "entityType", entityType,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find all " + entityType + " entities", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        try {
            Key key = buildKey(id);
            T deletedItem = table.deleteItem(key);
            
            boolean deleted = deletedItem != null;
            logger.info("entity_delete", Map.of(
                "entityType", entityType,
                "id", id,
                "deleted", deleted
            ));
            
            return deleted;
        } catch (DynamoDbException e) {
            logger.error("entity_delete_error", Map.of(
                "entityType", entityType,
                "id", id,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to delete " + entityType + " by id: " + id, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }

    @Override
    public T update(T entity) {
        String id = extractId(entity);
        if (!existsById(id)) {
            throw new ResourceNotFoundException(entityType, id);
        }
        return save(entity);
    }

    /**
     * Build the DynamoDB key for the given entity ID.
     * Subclasses must implement this to provide the correct partition and sort key.
     */
    protected abstract Key buildKey(String id);

    /**
     * Extract the entity ID from the entity object.
     * Subclasses must implement this to provide the correct ID extraction logic.
     */
    protected abstract String extractId(T entity);

    /**
     * Build filter expression for entity type.
     * Default implementation filters by entityType attribute.
     */
    protected software.amazon.awssdk.enhanced.dynamodb.Expression buildEntityTypeFilter() {
        return software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
            .expression("entityType = :entityType")
            .putExpressionValue(":entityType", 
                software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                    .s(entityType)
                    .build())
            .build();
    }
}