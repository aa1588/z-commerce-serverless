package com.zcommerce.shared.service;

import com.zcommerce.shared.logging.StructuredLogger;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.ProductRepository;

import java.util.Map;

/**
 * Service for handling cross-service integration for product operations.
 * Coordinates between product and cart services.
 */
public class ProductIntegrationService {

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final StructuredLogger logger;

    public ProductIntegrationService(ProductRepository productRepository, CartRepository cartRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.logger = new StructuredLogger("product-integration-service");
    }

    /**
     * Delete product and handle dependencies.
     * Removes product from all carts before deletion.
     */
    public void deleteProductWithDependencies(String productId) {
        logger.info("product_deletion_with_dependencies_started", Map.of("productId", productId));

        // Remove product from all carts first
        int cartItemsRemoved = cartRepository.deleteByProductId(productId);
        
        if (cartItemsRemoved > 0) {
            logger.info("cart_items_removed_for_deleted_product", Map.of(
                "productId", productId,
                "cartItemsRemoved", cartItemsRemoved
            ));
        }

        // Delete the product
        productRepository.deleteById(productId);

        logger.info("product_deleted_with_dependencies", Map.of(
            "productId", productId,
            "cartItemsRemoved", cartItemsRemoved
        ));
    }
}