package com.scalecart.event.publisher;

import com.scalecart.event.model.InventoryUpdatedEvent;
import com.scalecart.event.model.OrderCreatedEvent;
import com.scalecart.event.model.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventPublisher {

    private final ApplicationEventPublisher springEventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${scalecart.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${scalecart.kafka.topics.order-created:order-created-events}")
    private String orderCreatedTopic;

    @Value("${scalecart.kafka.topics.payment-processed:payment-processed-events}")
    private String paymentProcessedTopic;

    @Value("${scalecart.kafka.topics.inventory-updated:inventory-updated-events}")
    private String inventoryUpdatedTopic;

    public EventPublisher(
            ApplicationEventPublisher springEventPublisher,
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.springEventPublisher = springEventPublisher;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent for order: {}", event.getOrderNumber());
        springEventPublisher.publishEvent(event);

        if (kafkaEnabled && kafkaTemplate != null) {
            try {
                kafkaTemplate.send(orderCreatedTopic, String.valueOf(event.getOrderId()), event)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("Kafka: Successfully sent OrderCreatedEvent [offset: {}]", result.getRecordMetadata().offset());
                            } else {
                                log.warn("Kafka: Failed to send OrderCreatedEvent, event handled locally: {}", ex.getMessage());
                            }
                        });
            } catch (Exception ex) {
                log.warn("Kafka send exception for OrderCreatedEvent: {}", ex.getMessage());
            }
        }
    }

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Publishing PaymentProcessedEvent for order: {}, status: {}", event.getOrderId(), event.getStatus());
        springEventPublisher.publishEvent(event);

        if (kafkaEnabled && kafkaTemplate != null) {
            try {
                kafkaTemplate.send(paymentProcessedTopic, String.valueOf(event.getOrderId()), event)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("Kafka: Successfully sent PaymentProcessedEvent [offset: {}]", result.getRecordMetadata().offset());
                            } else {
                                log.warn("Kafka: Failed to send PaymentProcessedEvent, event handled locally: {}", ex.getMessage());
                            }
                        });
            } catch (Exception ex) {
                log.warn("Kafka send exception for PaymentProcessedEvent: {}", ex.getMessage());
            }
        }
    }

    public void publishInventoryUpdated(InventoryUpdatedEvent event) {
        log.info("Publishing InventoryUpdatedEvent for product: {}, operation: {}", event.getProductId(), event.getOperationType());
        springEventPublisher.publishEvent(event);

        if (kafkaEnabled && kafkaTemplate != null) {
            try {
                kafkaTemplate.send(inventoryUpdatedTopic, String.valueOf(event.getProductId()), event);
            } catch (Exception ex) {
                log.warn("Kafka send exception for InventoryUpdatedEvent: {}", ex.getMessage());
            }
        }
    }
}
