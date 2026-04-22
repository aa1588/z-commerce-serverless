package com.zcommerce.payments;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Payment;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.PaymentRepository;
import com.zcommerce.shared.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentHandler scenarios including payment validation with invalid data,
 * missing order references, and payment success/failure handling with proper logging.
 */
class PaymentScenariosTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Context context;

    private PaymentHandler paymentHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentHandler = new PaymentHandler(paymentRepository, orderRepository, cartRepository, productRepository);
        objectMapper = new ObjectMapper();
    }

    @Test
    void processPayment_WithInvalidOrderId_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = "not-a-uuid";
        request.paymentMethod = "CREDIT_CARD";
        request.cardNumber = "4111111111111111";
        request.cardHolder = "John Doe";
        request.expiryDate = "12/25";
        request.cvv = "123";

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Order ID"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithMissingPaymentMethod_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        // Missing payment method

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Payment method"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithInvalidPaymentMethod_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "INVALID_METHOD";

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid payment method"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithMissingCardDetails_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "CREDIT_CARD";
        // Missing card details

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Card number"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithInvalidCardNumber_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "CREDIT_CARD";
        request.cardNumber = "123"; // Too short
        request.cardHolder = "John Doe";
        request.expiryDate = "12/25";
        request.cvv = "123";

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid card number format"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithMissingPayPalEmail_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "PAYPAL";
        // Missing PayPal email

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("PayPal email"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithInvalidPayPalEmail_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "PAYPAL";
        request.paypalEmail = "invalid-email"; // Invalid email format

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("email"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithMissingBankDetails_Returns400() throws Exception {
        // Arrange
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = UUID.randomUUID().toString();
        request.paymentMethod = "BANK_TRANSFER";
        // Missing bank details

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Bank account"));
        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithNonExistentOrder_Returns404() throws Exception {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = orderId;
        request.paymentMethod = "CREDIT_CARD";
        request.cardNumber = "4111111111111111";
        request.cardHolder = "John Doe";
        request.expiryDate = "12/25";
        request.cvv = "123";

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Order"));
        assertTrue(response.getBody().contains("not found"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithCompletedOrder_Returns409() throws Exception {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = orderId;
        request.paymentMethod = "CREDIT_CARD";
        request.cardNumber = "4111111111111111";
        request.cardHolder = "John Doe";
        request.expiryDate = "12/25";
        request.cvv = "123";

        Order order = createTestOrder(orderId);
        order.setStatus(Order.OrderStatus.COMPLETED); // Already completed
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("Cannot process payment for order with status: COMPLETED"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_WithValidCreditCard_ProcessesSuccessfully() throws Exception {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = orderId;
        request.paymentMethod = "CREDIT_CARD";
        request.cardNumber = "4111111111111111";
        request.cardHolder = "John Doe";
        request.expiryDate = "12/25";
        request.cvv = "123";

        Order order = createTestOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("transactionId"));
        assertTrue(response.getBody().contains("orderId"));
        
        // Verify payment was saved
        verify(paymentRepository).save(argThat(payment -> 
            payment.getOrderId().equals(orderId) &&
            payment.getPaymentMethod() == Payment.PaymentMethod.CREDIT_CARD &&
            payment.getAmount().equals(order.getTotalAmount())
        ));
        
        // Verify order status was updated (order is saved multiple times during processing)
        verify(orderRepository, atLeastOnce()).save(argThat(savedOrder -> 
            savedOrder.getOrderId().equals(orderId) &&
            (savedOrder.getStatus() == Order.OrderStatus.PROCESSING || 
             savedOrder.getStatus() == Order.OrderStatus.COMPLETED)
        ));
    }

    @Test
    void processPayment_WithValidPayPal_ProcessesSuccessfully() throws Exception {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = orderId;
        request.paymentMethod = "PAYPAL";
        request.paypalEmail = "user@example.com";

        Order order = createTestOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("transactionId"));
        
        // Verify payment was saved with correct method
        verify(paymentRepository).save(argThat(payment -> 
            payment.getPaymentMethod() == Payment.PaymentMethod.PAYPAL
        ));
    }

    @Test
    void processPayment_WithValidBankTransfer_ProcessesSuccessfully() throws Exception {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        PaymentHandler.ProcessPaymentRequest request = new PaymentHandler.ProcessPaymentRequest();
        request.orderId = orderId;
        request.paymentMethod = "BANK_TRANSFER";
        request.bankAccount = "1234567890";
        request.routingNumber = "021000021";

        Order order = createTestOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        APIGatewayProxyRequestEvent event = createProcessPaymentRequest(request);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("transactionId"));
        
        // Verify payment was saved with correct method
        verify(paymentRepository).save(argThat(payment -> 
            payment.getPaymentMethod() == Payment.PaymentMethod.BANK_TRANSFER
        ));
    }

    @Test
    void getPayment_WithInvalidTransactionId_Returns400() throws Exception {
        // Arrange
        String invalidTransactionId = "not-a-uuid";
        APIGatewayProxyRequestEvent event = createGetPaymentRequest(invalidTransactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Transaction ID"));
        verify(paymentRepository, never()).findById(any());
    }

    @Test
    void getPayment_WithNonExistentTransaction_Returns404() throws Exception {
        // Arrange
        String transactionId = UUID.randomUUID().toString();
        when(paymentRepository.findById(transactionId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = createGetPaymentRequest(transactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Payment"));
        assertTrue(response.getBody().contains("not found"));
    }

    @Test
    void getPayment_WithValidTransaction_ReturnsPaymentDetails() throws Exception {
        // Arrange
        String transactionId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        
        Payment payment = new Payment(transactionId, orderId, new BigDecimal("99.99"), Payment.PaymentMethod.CREDIT_CARD);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaymentDetails("****1111");
        
        when(paymentRepository.findById(transactionId)).thenReturn(Optional.of(payment));

        APIGatewayProxyRequestEvent event = createGetPaymentRequest(transactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains(transactionId));
        assertTrue(response.getBody().contains(orderId));
        assertTrue(response.getBody().contains("COMPLETED"));
        assertTrue(response.getBody().contains("CREDIT_CARD"));
    }

    @Test
    void refundPayment_WithInvalidTransactionId_Returns400() throws Exception {
        // Arrange
        String invalidTransactionId = "not-a-uuid";
        APIGatewayProxyRequestEvent event = createRefundPaymentRequest(invalidTransactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Transaction ID"));
        verify(paymentRepository, never()).findById(any());
    }

    @Test
    void refundPayment_WithNonExistentTransaction_Returns404() throws Exception {
        // Arrange
        String transactionId = UUID.randomUUID().toString();
        when(paymentRepository.findById(transactionId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = createRefundPaymentRequest(transactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Payment"));
        assertTrue(response.getBody().contains("not found"));
    }

    @Test
    void refundPayment_WithFailedPayment_Returns409() throws Exception {
        // Arrange
        String transactionId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        
        Payment payment = new Payment(transactionId, orderId, new BigDecimal("99.99"), Payment.PaymentMethod.CREDIT_CARD);
        payment.setStatus(Payment.PaymentStatus.FAILED); // Cannot refund failed payment
        
        when(paymentRepository.findById(transactionId)).thenReturn(Optional.of(payment));

        APIGatewayProxyRequestEvent event = createRefundPaymentRequest(transactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("Cannot refund payment with status: FAILED"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_WithCompletedPayment_ProcessesRefundSuccessfully() throws Exception {
        // Arrange
        String transactionId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        
        Payment payment = new Payment(transactionId, orderId, new BigDecimal("99.99"), Payment.PaymentMethod.CREDIT_CARD);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        
        Order order = createTestOrder(orderId);
        
        when(paymentRepository.findById(transactionId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        APIGatewayProxyRequestEvent event = createRefundPaymentRequest(transactionId);

        // Act
        APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("refunded"));
        
        // Verify payment status was updated to REFUNDED
        verify(paymentRepository).save(argThat(savedPayment -> 
            savedPayment.getStatus() == Payment.PaymentStatus.REFUNDED
        ));
        
        // Verify order status was updated to CANCELLED
        verify(orderRepository).save(argThat(savedOrder -> 
            savedOrder.getStatus() == Order.OrderStatus.CANCELLED
        ));
    }

    // Helper methods

    private Order createTestOrder(String orderId) {
        OrderItem item = new OrderItem("product1", "Test Product", 1, new BigDecimal("99.99"));
        Order order = new Order(orderId, UUID.randomUUID().toString(), Arrays.asList(item), new BigDecimal("99.99"));
        order.setStatus(Order.OrderStatus.PENDING);
        return order;
    }

    private APIGatewayProxyRequestEvent createProcessPaymentRequest(PaymentHandler.ProcessPaymentRequest request) throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("POST");
        event.setPath("/payments/process");
        event.setBody(objectMapper.writeValueAsString(request));
        return event;
    }

    private APIGatewayProxyRequestEvent createGetPaymentRequest(String transactionId) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("GET");
        event.setPath("/payments/" + transactionId);
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("transactionId", transactionId);
        event.setPathParameters(pathParams);
        
        return event;
    }

    private APIGatewayProxyRequestEvent createRefundPaymentRequest(String transactionId) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("POST");
        event.setPath("/payments/" + transactionId + "/refund");
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("transactionId", transactionId);
        event.setPathParameters(pathParams);
        
        return event;
    }
}