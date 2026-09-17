package edu.cit.loquillano.shop;

import edu.cit.loquillano.event.LowStockEvent;
import edu.cit.loquillano.event.OrderItemEvent;
import edu.cit.loquillano.event.OrderPlacedEvent;
import edu.cit.loquillano.event.OrderRejectedEvent;
import edu.cit.loquillano.inventory.InventoryItem;
import edu.cit.loquillano.inventory.InventoryService;
import edu.cit.loquillano.inventory.ProductNotFoundException;
import edu.cit.loquillano.shop.dto.InventorySnapshot;
import edu.cit.loquillano.shop.dto.ItemOutcome;
import edu.cit.loquillano.shop.dto.OrderItemRequest;
import edu.cit.loquillano.shop.dto.OrderItemSummary;
import edu.cit.loquillano.shop.dto.OrderResponse;
import edu.cit.loquillano.shop.dto.OrderSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order module entry point. Talks to Inventory purely in-process through
 * the InventoryService interface, and talks to Notification purely
 * through published events — never a direct call either way.
 */
@Service
public class OrderService {

    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_RESERVED = "RESERVED";
    private static final String OUTCOME_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    private static final String OUTCOME_NOT_FOUND = "NOT_FOUND";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    public OrderService(InventoryService inventoryService,
                         OrderRepository orderRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(List<OrderItemRequest> requests) {
        List<LineValidation> validations = validateAll(requests);
        boolean allOk = validations.stream().allMatch(v -> OUTCOME_OK.equals(v.outcome));

        Order order = new Order(allOk ? Order.STATUS_CONFIRMED : Order.STATUS_REJECTED, null);
        for (LineValidation v : validations) {
            order.addItem(new OrderItem(v.productId, v.quantity));
        }

        if (!allOk) {
            String reason = buildRejectionReason(validations);
            order.setReason(reason);
            orderRepository.save(order);

            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));

            List<ItemOutcome> outcomes = validations.stream()
                    .map(v -> new ItemOutcome(v.productId, v.outcome))
                    .toList();
            List<InventorySnapshot> snapshots = currentSnapshots(requests);
            return new OrderResponse(order.getOrderId(), Order.STATUS_REJECTED, reason, outcomes, snapshots);
        }

        // Every line item passed validation — now actually reserve each one.
        List<InventorySnapshot> snapshots = new ArrayList<>();
        for (LineValidation v : validations) {
            InventoryItem reserved = inventoryService.reserve(v.productId, v.quantity);
            snapshots.add(toSnapshot(reserved));
            if (reserved.getStock() < lowStockThreshold) {
                eventPublisher.publishEvent(
                        new LowStockEvent(reserved.getProductId(), reserved.getName(), reserved.getStock()));
            }
        }

        orderRepository.save(order);

        List<OrderItemEvent> itemEvents = validations.stream()
                .map(v -> new OrderItemEvent(v.productId, v.quantity))
                .toList();
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), itemEvents));

        List<ItemOutcome> outcomes = validations.stream()
                .map(v -> new ItemOutcome(v.productId, OUTCOME_RESERVED))
                .toList();

        return new OrderResponse(order.getOrderId(), Order.STATUS_CONFIRMED, null, outcomes, snapshots);
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id " + orderId));

        if (Order.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderAlreadyCancelledException("Order " + orderId + " is already cancelled");
        }

        // Only a previously CONFIRMED order actually holds reserved stock.
        if (Order.STATUS_CONFIRMED.equals(order.getStatus())) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(Order.STATUS_CANCELLED);
        orderRepository.save(order);
    }

    public List<OrderSummary> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(order -> new OrderSummary(
                        order.getOrderId(),
                        order.getStatus(),
                        order.getReason(),
                        order.getCreatedAt(),
                        order.getItems().stream()
                                .map(item -> new OrderItemSummary(item.getProductId(), item.getQuantity()))
                                .toList()))
                .toList();
    }

    // --- helpers -----------------------------------------------------

    private List<LineValidation> validateAll(List<OrderItemRequest> requests) {
        List<LineValidation> results = new ArrayList<>();
        for (OrderItemRequest req : requests) {
            try {
                InventoryItem item = inventoryService.getItem(req.getProductId());
                if (req.getQuantity() > item.getStock()) {
                    results.add(new LineValidation(req.getProductId(), req.getQuantity(),
                            OUTCOME_INSUFFICIENT_STOCK,
                            "requested " + req.getQuantity() + " but only " + item.getStock() + " available"));
                } else {
                    results.add(new LineValidation(req.getProductId(), req.getQuantity(), OUTCOME_OK, null));
                }
            } catch (ProductNotFoundException ex) {
                results.add(new LineValidation(req.getProductId(), req.getQuantity(),
                        OUTCOME_NOT_FOUND, "no such product"));
            }
        }
        return results;
    }

    private String buildRejectionReason(List<LineValidation> validations) {
        return validations.stream()
                .filter(v -> !OUTCOME_OK.equals(v.outcome))
                .map(v -> v.productId + ": " + v.detail)
                .collect(Collectors.joining("; "));
    }

    private List<InventorySnapshot> currentSnapshots(List<OrderItemRequest> requests) {
        Set<String> productIds = requests.stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<InventorySnapshot> snapshots = new ArrayList<>();
        for (String productId : productIds) {
            try {
                snapshots.add(toSnapshot(inventoryService.getItem(productId)));
            } catch (ProductNotFoundException ignored) {
                // Product doesn't exist at all — nothing to show for it.
            }
        }
        return snapshots;
    }

    private InventorySnapshot toSnapshot(InventoryItem item) {
        return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
    }

    private record LineValidation(String productId, int quantity, String outcome, String detail) {
    }
}
