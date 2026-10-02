package com.scalecart.event.consumer;

import com.scalecart.event.model.InventoryUpdatedEvent;
import com.scalecart.event.model.OrderCreatedEvent;
import com.scalecart.event.model.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalEventConsumer {

    @EventListener
    public void handleLocalOrderCreated(OrderCreatedEvent event) {
        log.info("[SPRING EVENT BUS] Local OrderCreatedEvent received for order: {}", event.getOrderNumber());
    }

    @EventListener
    public void handleLocalPaymentProcessed(PaymentProcessedEvent event) {
        log.info("[SPRING EVENT BUS] Local PaymentProcessedEvent received for order: {}, status: {}",
                event.getOrderId(), event.getStatus());
    }

    @EventListener
    public void handleLocalInventoryUpdated(InventoryUpdatedEvent event) {
        log.info("[SPRING EVENT BUS] Local InventoryUpdatedEvent received for product: {}, op: {}",
                event.getProductId(), event.getOperationType());
    }
}
