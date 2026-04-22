package com.zcommerce.shared.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zcommerce.shared.exception.*;
import com.zcommerce.shared.logging.StructuredLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Base Lambda handler that provides common functionality for all Z-Commerce Lambda functions.
 * Handles request/response processing, error handling, and logging.
 */
public abstract class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    protected static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    protected StructuredLogger logger;

    protected abstract String getServiceName();
    protected abstract APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Initialize logger with request context
        this.logger = new StructuredLogger(getServiceName(), context.getAwsRequestId());
        
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("httpMethod", request.getHttpMethod());
        requestContext.put("path", request.getPath());
        requestContext.put("requestId", context.getAwsRequestId());
        
        logger.info("Request received", requestContext);

        try {
            APIGatewayProxyResponseEvent response = processRequest(request, context);
            
            Map<String, Object> responseContext = new HashMap<>();
            responseContext.put("statusCode", response.getStatusCode());
            logger.info("Request completed", responseContext);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Request failed", requestContext, e);
            return handleException(e);
        }
    }

    protected APIGatewayProxyResponseEvent handleException(Exception e) {
        if (e instanceof ValidationException) {
            return createErrorResponse(400, ((ValidationException) e).getErrorCode(), e.getMessage(), 
                                     ((ValidationException) e).getContext());
        } else if (e instanceof AuthenticationException) {
            return createErrorResponse(401, ((AuthenticationException) e).getErrorCode(), e.getMessage(), 
                                     ((AuthenticationException) e).getContext());
        } else if (e instanceof ResourceNotFoundException) {
            return createErrorResponse(404, ((ResourceNotFoundException) e).getErrorCode(), e.getMessage(), 
                                     ((ResourceNotFoundException) e).getContext());
        } else if (e instanceof ConflictException) {
            return createErrorResponse(409, ((ConflictException) e).getErrorCode(), e.getMessage(), 
                                     ((ConflictException) e).getContext());
        } else {
            // Log full exception details for unexpected errors
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("exceptionType", e.getClass().getSimpleName());
            logger.error("Unexpected error", errorContext, e);
            
            return createErrorResponse(500, "INTERNAL_ERROR", "Internal server error", null);
        }
    }

    protected APIGatewayProxyResponseEvent createSuccessResponse(Object data) {
        return createSuccessResponse(data, "Operation completed successfully");
    }

    protected APIGatewayProxyResponseEvent createSuccessResponse(Object data, String message) {
        ApiResponse<?> response = ApiResponse.success(data, message);
        return createResponse(200, response.toJson());
    }

    protected APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String errorCode, 
                                                             String message, Map<String, Object> details) {
        ApiResponse<?> response = ApiResponse.error(errorCode, message, details);
        return createResponse(statusCode, response.toJson());
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }

    protected <T> T parseRequestBody(String body, Class<T> clazz) {
        try {
            return objectMapper.readValue(body, clazz);
        } catch (Exception e) {
            throw new ValidationException("Invalid request body format: " + e.getMessage());
        }
    }

    protected String getPathParameter(APIGatewayProxyRequestEvent request, String paramName) {
        Map<String, String> pathParams = request.getPathParameters();
        if (pathParams == null || !pathParams.containsKey(paramName)) {
            throw new ValidationException("Missing required path parameter: " + paramName);
        }
        return pathParams.get(paramName);
    }

    protected String getQueryParameter(APIGatewayProxyRequestEvent request, String paramName) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        return queryParams != null ? queryParams.get(paramName) : null;
    }
}