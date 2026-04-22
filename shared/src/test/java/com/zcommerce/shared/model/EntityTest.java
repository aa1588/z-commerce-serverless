package com.zcommerce.shared.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for entity classes to verify they work correctly.
 */
class EntityTest {

    @Test
    void testUserEntity() {
        String userId = "user123";
        String email = "test@example.com";
        String passwordHash = "hashedPassword";
        String firstName = "John";
        String lastName = "Doe";

        User user = new User(userId, email, passwordHash, firstName, lastName);

        assertEquals(userId, user.getUserId());
        assertEquals(email, user.getEmail());
        assertEquals(passwordHash, user.getPasswordHash());
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertEquals("USER", user.getEntityType());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("PROFILE", user.getSk());
    }

    @Test
    void testProductEntity() {
        String productId = "prod123";
        String name = "Test Product";
        String description = "A test product";
        BigDecimal price = new BigDecimal("99.99");
        Integer inventory = 50;
        String category = "Electronics";

        Product product = new Product(productId, name, description, price, inventory, category);

        assertEquals(productId, product.getProductId());
        assertEquals(name, product.getName());
        assertEquals(description, product.getDescription());
        assertEquals(price, product.getPrice());
        assertEquals(inventory, product.getInventory());
        assertEquals(category, product.getCategory());
        assertEquals("PRODUCT", product.getEntityType());
        assertTrue(product.isAvailable());
        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());
        assertEquals("PRODUCT#" + productId, product.getPk());
        assertEquals("DETAILS", product.getSk());
    }

    @Test
    void testCartItemEntity() {
        String userId = "user123";
        String productId = "prod123";
        Integer quantity = 2;

        CartItem cartItem = new CartItem(userId, productId, quantity);

        assertEquals(userId, cartItem.getUserId());
        assertEquals(productId, cartItem.getProductId());
        assertEquals(quantity, cartItem.getQuantity());
        assertEquals("CART_ITEM", cartItem.getEntityType());
        assertNotNull(cartItem.getAddedAt());
        assertEquals("USER#" + userId, cartItem.getPk());
        assertEquals("CART#PRODUCT#" + productId, cartItem.getSk());
    }

    @Test
    void testOrderEntity() {
        String orderId = "order123";
        String userId = "user123";
        OrderItem item1 = new OrderItem("prod1", "Product 1", 2, new BigDecimal("10.00"));
        OrderItem item2 = new OrderItem("prod2", "Product 2", 1, new BigDecimal("20.00"));
        BigDecimal totalAmount = new BigDecimal("40.00");

        Order order = new Order(orderId, userId, Arrays.asList(item1, item2), totalAmount);

        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(totalAmount, order.getTotalAmount());
        assertEquals(2, order.getItems().size());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        assertEquals("ORDER", order.getEntityType());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
    }

    @Test
    void testPaymentEntity() {
        String transactionId = "txn123";
        String orderId = "order123";
        BigDecimal amount = new BigDecimal("99.99");
        Payment.PaymentMethod paymentMethod = Payment.PaymentMethod.CREDIT_CARD;

        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);

        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(amount, payment.getAmount());
        assertEquals(paymentMethod, payment.getPaymentMethod());
        assertEquals(Payment.PaymentStatus.PENDING, payment.getStatus());
        assertEquals("PAYMENT", payment.getEntityType());
        assertNotNull(payment.getProcessedAt());
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("TRANSACTION", payment.getSk());
    }

    @Test
    void testOrderItemCalculation() {
        String productId = "prod123";
        String productName = "Test Product";
        Integer quantity = 3;
        BigDecimal price = new BigDecimal("15.50");

        OrderItem orderItem = new OrderItem(productId, productName, quantity, price);

        assertEquals(productId, orderItem.getProductId());
        assertEquals(productName, orderItem.getProductName());
        assertEquals(quantity, orderItem.getQuantity());
        assertEquals(price, orderItem.getPrice());
        assertEquals(new BigDecimal("46.50"), orderItem.getTotalPrice());
    }
}