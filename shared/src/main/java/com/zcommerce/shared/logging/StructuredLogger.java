package com.zcommerce.shared.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Structured logger that outputs JSON-formatted log entries compatible with AWS CloudWatch.
 * Provides consistent logging format across all Z-Commerce services.
 */
public class StructuredLogger {
    private static final Logger logger = LoggerFactory.getLogger(StructuredLogger.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private final String serviceName;
    private final String requestId;

    public StructuredLogger(String serviceName) {
        this.serviceName = serviceName;
        this.requestId = generateRequestId();
    }

    public StructuredLogger(String serviceName, String requestId) {
        this.serviceName = serviceName;
        this.requestId = requestId;
    }

    public void info(String event, Map<String, Object> context) {
        logEvent("INFO", event, context, null);
    }

    public void warn(String event, Map<String, Object> context) {
        logEvent("WARN", event, context, null);
    }

    public void error(String event, Map<String, Object> context, Throwable throwable) {
        logEvent("ERROR", event, context, throwable);
    }

    public void debug(String event, Map<String, Object> context) {
        logEvent("DEBUG", event, context, null);
    }

    private void logEvent(String level, String event, Map<String, Object> context, Throwable throwable) {
        try {
            LogEntry entry = LogEntry.builder()
                    .timestamp(Instant.now())
                    .level(level)
                    .service(serviceName)
                    .requestId(requestId)
                    .event(event)
                    .context(context != null ? context : new HashMap<>())
                    .error(throwable != null ? createErrorInfo(throwable) : null)
                    .build();

            String jsonLog = objectMapper.writeValueAsString(entry);
            
            switch (level) {
                case "ERROR" -> logger.error(jsonLog, throwable);
                case "WARN" -> logger.warn(jsonLog);
                case "DEBUG" -> logger.debug(jsonLog);
                default -> logger.info(jsonLog);
            }
        } catch (Exception e) {
            // Fallback to simple logging if JSON serialization fails
            logger.error("Failed to serialize log entry: {}", e.getMessage(), e);
            logger.info("Original event: {} - {}", event, context);
        }
    }

    private Map<String, Object> createErrorInfo(Throwable throwable) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("message", throwable.getMessage());
        errorInfo.put("type", throwable.getClass().getSimpleName());
        
        if (throwable.getCause() != null) {
            errorInfo.put("cause", throwable.getCause().getMessage());
        }
        
        return errorInfo;
    }

    private String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString((int) (Math.random() * 0x10000));
    }

    public static class LogEntry {
        private Instant timestamp;
        private String level;
        private String service;
        private String requestId;
        private String event;
        private Map<String, Object> context;
        private Map<String, Object> error;

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Instant getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getService() { return service; }
        public String getRequestId() { return requestId; }
        public String getEvent() { return event; }
        public Map<String, Object> getContext() { return context; }
        public Map<String, Object> getError() { return error; }

        public static class Builder {
            private final LogEntry entry = new LogEntry();

            public Builder timestamp(Instant timestamp) {
                entry.timestamp = timestamp;
                return this;
            }

            public Builder level(String level) {
                entry.level = level;
                return this;
            }

            public Builder service(String service) {
                entry.service = service;
                return this;
            }

            public Builder requestId(String requestId) {
                entry.requestId = requestId;
                return this;
            }

            public Builder event(String event) {
                entry.event = event;
                return this;
            }

            public Builder context(Map<String, Object> context) {
                entry.context = context;
                return this;
            }

            public Builder error(Map<String, Object> error) {
                entry.error = error;
                return this;
            }

            public LogEntry build() {
                return entry;
            }
        }
    }
}