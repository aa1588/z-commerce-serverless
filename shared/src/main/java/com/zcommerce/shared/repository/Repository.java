package com.zcommerce.shared.repository;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface defining common CRUD operations.
 * 
 * @param <T> Entity type
 * @param <ID> Primary key type
 */
public interface Repository<T, ID> {
    
    /**
     * Save an entity to the repository.
     * 
     * @param entity The entity to save
     * @return The saved entity
     */
    T save(T entity);
    
    /**
     * Find an entity by its primary key.
     * 
     * @param id The primary key
     * @return Optional containing the entity if found, empty otherwise
     */
    Optional<T> findById(ID id);
    
    /**
     * Find all entities of this type.
     * 
     * @return List of all entities
     */
    List<T> findAll();
    
    /**
     * Delete an entity by its primary key.
     * 
     * @param id The primary key
     * @return true if the entity was deleted, false if it didn't exist
     */
    boolean deleteById(ID id);
    
    /**
     * Check if an entity exists by its primary key.
     * 
     * @param id The primary key
     * @return true if the entity exists, false otherwise
     */
    boolean existsById(ID id);
    
    /**
     * Update an existing entity.
     * 
     * @param entity The entity to update
     * @return The updated entity
     * @throws com.zcommerce.shared.exception.ResourceNotFoundException if entity doesn't exist
     */
    T update(T entity);
}