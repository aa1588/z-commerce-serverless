package com.zcommerce.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.zcommerce.shared.api.LambdaHandler;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.ProductRepository;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.impl.DynamoDbProductRepository;
import com.zcommerce.shared.repository.impl.DynamoDbCartRepository;
import com.zcommerce.shared.service.ProductIntegrationService;
import com.zcommerce.shared.util.ValidationUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lambda handler for product catalog operations.
 * Handles product CRUD operations and inventory management.
 */
public class ProductHandler extends LambdaHandler {

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final ProductIntegrationService productIntegrationService;

    public ProductHandler() {
        this(new DynamoDbProductRepository(), new DynamoDbCartRepository());
    }

    public ProductHandler(ProductRepository productRepository, CartRepository cartRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.productIntegrationService = new ProductIntegrationService(productRepository, cartRepository);
    }

    @Override
    protected String getServiceName() {
        return "product-service";
    }

    @Override
    protected APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Processing product service request",
                   Map.of("method", httpMethod, "path", path));

        // Route based on HTTP method and path
        if ("GET".equals(httpMethod) && path.matches(".*/products$")) {
            return handleListProducts(request);
        } else if ("GET".equals(httpMethod) && path.matches(".*/products/[^/]+$")) {
            return handleGetProduct(request);
        } else if ("POST".equals(httpMethod) && path.matches(".*/products$")) {
            return handleCreateProduct(request);
        } else if ("PUT".equals(httpMethod) && path.matches(".*/products/[^/]+$")) {
            return handleUpdateProduct(request);
        } else if ("DELETE".equals(httpMethod) && path.matches(".*/products/[^/]+$")) {
            return handleDeleteProduct(request);
        }

        throw new ValidationException("Unsupported operation: " + httpMethod + " " + path);
    }

    /**
     * Handle list all products
     * GET /products
     */
    private APIGatewayProxyResponseEvent handleListProducts(APIGatewayProxyRequestEvent request) {
        String category = getQueryParameter(request, "category");
        String availableOnly = getQueryParameter(request, "availableOnly");

        List<Product> products;

        if (category != null && !category.isEmpty()) {
            products = productRepository.findByCategory(category);
        } else if ("true".equalsIgnoreCase(availableOnly)) {
            products = productRepository.findAvailableProducts();
        } else {
            products = productRepository.findAll();
        }

        logger.info("products_listed", Map.of(
            "count", products.size(),
            "category", category != null ? category : "all"
        ));

        List<Map<String, Object>> productList = products.stream()
            .map(this::toProductResponse)
            .collect(Collectors.toList());

        return createSuccessResponse(
            Map.of("products", productList, "count", products.size()),
            "Products retrieved successfully"
        );
    }

    /**
     * Handle get single product
     * GET /products/{productId}
     */
    private APIGatewayProxyResponseEvent handleGetProduct(APIGatewayProxyRequestEvent request) {
        String productId = getPathParameter(request, "productId");

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new ResourceNotFoundException("Product", productId);
        }

        Product product = productOpt.get();

        logger.info("product_retrieved", Map.of(
            "productId", productId
        ));

        return createSuccessResponse(
            toProductResponse(product),
            "Product retrieved successfully"
        );
    }

    /**
     * Handle create new product (admin only)
     * POST /products
     */
    private APIGatewayProxyResponseEvent handleCreateProduct(APIGatewayProxyRequestEvent request) {
        CreateProductRequest createRequest = parseRequestBody(request.getBody(), CreateProductRequest.class);

        // Validate input
        ValidationUtils.validateRequired(createRequest.name, "Name");
        ValidationUtils.validateRequired(createRequest.description, "Description");
        ValidationUtils.validatePositive(createRequest.price, "Price");
        ValidationUtils.validateNonNegative(createRequest.inventory, "Inventory");
        ValidationUtils.validateRequired(createRequest.category, "Category");

        // Create product
        String productId = UUID.randomUUID().toString();
        Product product = new Product(
            productId,
            createRequest.name,
            createRequest.description,
            createRequest.price,
            createRequest.inventory,
            createRequest.category
        );

        productRepository.save(product);

        logger.info("product_created", Map.of(
            "productId", productId,
            "name", product.getName(),
            "category", product.getCategory()
        ));

        return createSuccessResponse(
            toProductResponse(product),
            "Product created successfully"
        );
    }

    /**
     * Handle update product (admin only)
     * PUT /products/{productId}
     */
    private APIGatewayProxyResponseEvent handleUpdateProduct(APIGatewayProxyRequestEvent request) {
        String productId = getPathParameter(request, "productId");

        UpdateProductRequest updateRequest = parseRequestBody(request.getBody(), UpdateProductRequest.class);

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new ResourceNotFoundException("Product", productId);
        }

        Product product = productOpt.get();

        // Update fields if provided
        if (updateRequest.name != null && !updateRequest.name.trim().isEmpty()) {
            product.setName(updateRequest.name);
        }
        if (updateRequest.description != null && !updateRequest.description.trim().isEmpty()) {
            product.setDescription(updateRequest.description);
        }
        if (updateRequest.price != null) {
            ValidationUtils.validatePositive(updateRequest.price, "Price");
            product.setPrice(updateRequest.price);
        }
        if (updateRequest.inventory != null) {
            ValidationUtils.validateNonNegative(updateRequest.inventory, "Inventory");
            product.setInventory(updateRequest.inventory);
        }
        if (updateRequest.category != null && !updateRequest.category.trim().isEmpty()) {
            product.setCategory(updateRequest.category);
        }

        product.setUpdatedAt(Instant.now());
        productRepository.save(product);

        logger.info("product_updated", Map.of(
            "productId", productId
        ));

        return createSuccessResponse(
            toProductResponse(product),
            "Product updated successfully"
        );
    }

    /**
     * Handle delete product (admin only)
     * DELETE /products/{productId}
     * Also removes product from all carts (handles dependencies)
     */
    private APIGatewayProxyResponseEvent handleDeleteProduct(APIGatewayProxyRequestEvent request) {
        String productId = getPathParameter(request, "productId");

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new ResourceNotFoundException("Product", productId);
        }

        // Use integration service to handle dependencies
        productIntegrationService.deleteProductWithDependencies(productId);

        logger.info("product_deleted", Map.of(
            "productId", productId
        ));

        return createSuccessResponse(
            Map.of("productId", productId, "deleted", true),
            "Product deleted successfully"
        );
    }

    /**
     * Convert Product to response map
     */
    private Map<String, Object> toProductResponse(Product product) {
        return Map.of(
            "productId", product.getProductId(),
            "name", product.getName(),
            "description", product.getDescription(),
            "price", product.getPrice(),
            "inventory", product.getInventory(),
            "category", product.getCategory(),
            "available", product.isAvailable(),
            "createdAt", product.getCreatedAt().toString(),
            "updatedAt", product.getUpdatedAt().toString()
        );
    }

    // Request DTOs
    public static class CreateProductRequest {
        public String name;
        public String description;
        public BigDecimal price;
        public Integer inventory;
        public String category;
    }

    public static class UpdateProductRequest {
        public String name;
        public String description;
        public BigDecimal price;
        public Integer inventory;
        public String category;
    }
}
