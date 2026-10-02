package com.scalecart.event.consumer;

import com.scalecart.event.model.InventoryUpdatedEvent;
import com.scalecart.event.model.OrderCreatedEvent;
import com.scalecart.event.model.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "scalecart.kafka.enabled", havingValue = "true")
public class KafkaEventConsumers {

    @KafkaListener(
            topics = "${scalecart.kafka.topics.order-created:order-created-events}",
            groupId = "scalecart-inventory-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderCreatedKafka(OrderCreatedEvent event) {
        log.info("[KAFKA CONSUMER] Received OrderCreatedEvent for order: {}, total: ${}, items count: {}",
                event.getOrderNumber(), event.getTotalAmount(), event.getItems().size());
    }

    @KafkaListener(
            topics = "${scalecart.kafka.topics.payment-processed:payment-processed-events}",
            groupId = "scalecart-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentProcessedKafka(PaymentProcessedEvent event) {
        log.info("[KAFKA CONSUMER] Received PaymentProcessedEvent for order ID: {}, txn: {}, status: {}",
                event.getOrderId(), event.getTransactionId(), event.getStatus());
    }

    @KafkaListener(
            topics = "${scalecart.kafka.topics.inventory-updated:inventory-updated-events}",
            groupId = "scalecart-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeInventoryUpdatedKafka(InventoryUpdatedEvent event) {
        log.info("[KAFKA CONSUMER] Received InventoryUpdatedEvent for product: {}, operation: {}, available: {}",
                event.getProductId(), event.getOperationType(), event.getAvailableStock());
    }
}
