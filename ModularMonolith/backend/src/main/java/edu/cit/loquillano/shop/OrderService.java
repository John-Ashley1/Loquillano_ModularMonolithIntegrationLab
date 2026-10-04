package edu.cit.loquillano.shop;

import edu.cit.loquillano.event.BackorderResolvedEvent;
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
import edu.cit.loquillano.shop.dto.PlacementResult;
import edu.cit.loquillano.supplier.ReorderResult;
import edu.cit.loquillano.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Order module entry point. Talks to Inventory purely in-process through
 * the InventoryService interface, to the supplier's Anti-Corruption Layer
 * purely through the SupplierGateway interface, and to everyone else
 * purely through published events — never a direct call to any
 * implementation. It has no idea where an order came from (React UI,
 * marketplace, ...): every source goes through the same logic.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_RESERVED = "RESERVED";
    private static final String OUTCOME_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    private static final String OUTCOME_NOT_FOUND = "NOT_FOUND";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SupplierGateway supplierGateway;
    private final TransactionTemplate requiresNewTx;
    private final SupplyReorderer supplyReorderer;

    @Value("${app.inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    @Value("${app.inventory.reorder-quantity:20}")
    private int reorderQuantity;

    @Value("${app.order.backorder-timeout-minutes:15}")
    private int backorderTimeoutMinutes;

    public OrderService(InventoryService inventoryService,
                         OrderRepository orderRepository,
                         ApplicationEventPublisher eventPublisher,
                         SupplierGateway supplierGateway,
                         PlatformTransactionManager transactionManager,
                         SupplyReorderer supplyReorderer) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.supplierGateway = supplierGateway;
        this.supplyReorderer = supplyReorderer;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------
    // Placing orders
    // ------------------------------------------------------------------

    /** Original all-or-nothing order (used by the React UI): CONFIRMED or REJECTED. */
    @Transactional
    public OrderResponse placeOrder(List<OrderItemRequest> requests) {
        lockInOrder(requests.stream().map(OrderItemRequest::getProductId).toList());
        List<LineValidation> validations = validateAll(requests);
        boolean allOk = validations.stream().allMatch(v -> OUTCOME_OK.equals(v.outcome));

        if (!allOk) {
            Order order = saveRejected(validations);
            List<ItemOutcome> outcomes = validations.stream()
                    .map(v -> new ItemOutcome(v.productId, v.outcome))
                    .toList();
            List<InventorySnapshot> snapshots = currentSnapshots(requests);
            return new OrderResponse(order.getOrderId(), Order.STATUS_REJECTED, order.getReason(), outcomes, snapshots);
        }

        Order order = new Order(Order.STATUS_CONFIRMED, null);
        for (LineValidation v : validations) {
            order.addItem(new OrderItem(v.productId, v.quantity));
        }

        // Every line item passed validation — now actually reserve each one.
        List<InventorySnapshot> snapshots = reserveAll(validations);
        orderRepository.save(order);
        publishPlaced(order.getOrderId(), validations);

        List<ItemOutcome> outcomes = validations.stream()
                .map(v -> new ItemOutcome(v.productId, OUTCOME_RESERVED))
                .toList();

        return new OrderResponse(order.getOrderId(), Order.STATUS_CONFIRMED, null, outcomes, snapshots);
    }

    /**
     * Same all-or-nothing rule as placeOrder, plus a third outcome: if the
     * order cannot be filled from stock now but a supplier delivery that is
     * already on its way will cover it, the order is stored as BACKORDERED
     * (no stock held) and filled automatically when the stock arrives.
     * Rejected only when nothing is coming. Duplicate lines for the same
     * product are merged.
     */
    @Transactional
    public PlacementResult placeOrderAllowingBackorder(List<OrderItemRequest> requests) {
        List<OrderItemRequest> lines = mergeLines(requests);
        lockInOrder(lines.stream().map(OrderItemRequest::getProductId).toList());
        List<LineValidation> validations = validateAll(lines);

        if (validations.stream().allMatch(v -> OUTCOME_OK.equals(v.outcome))) {
            Order order = new Order(Order.STATUS_CONFIRMED, null);
            for (LineValidation v : validations) {
                order.addItem(new OrderItem(v.productId, v.quantity));
            }
            reserveAll(validations);
            orderRepository.save(order);
            publishPlaced(order.getOrderId(), validations);
            return new PlacementResult(order.getOrderId(), Order.STATUS_CONFIRMED, null);
        }

        boolean anyUnknownProduct = validations.stream().anyMatch(v -> OUTCOME_NOT_FOUND.equals(v.outcome));
        if (!anyUnknownProduct && canBackorder(validations)) {
            Order order = new Order(Order.STATUS_BACKORDERED, "Waiting for supplier delivery");
            for (LineValidation v : validations) {
                order.addItem(new OrderItem(v.productId, v.quantity));
            }
            orderRepository.save(order);
            log.info("Order {} backordered: not enough stock now, supplier delivery on its way", order.getOrderId());
            return new PlacementResult(order.getOrderId(), Order.STATUS_BACKORDERED, order.getReason());
        }

        Order order = saveRejected(validations);
        return new PlacementResult(order.getOrderId(), Order.STATUS_REJECTED, order.getReason());
    }

    // ------------------------------------------------------------------
    // Cancelling
    // ------------------------------------------------------------------

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id " + orderId));

        if (Order.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderAlreadyCancelledException("Order " + orderId + " is already cancelled");
        }

        // Only a previously CONFIRMED order actually holds reserved stock.
        if (Order.STATUS_CONFIRMED.equals(order.getStatus())) {
            Map<String, Integer> perProduct = new TreeMap<>();
            for (OrderItem item : order.getItems()) {
                perProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
            perProduct.forEach(inventoryService::restock);
        }

        order.setStatus(Order.STATUS_CANCELLED);
        orderRepository.save(order);
    }

    /**
     * Current status of an order. Intentionally not @Transactional so that a
     * "not found" never marks the caller's transaction rollback-only.
     */
    public String getStatus(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getStatus)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id " + orderId));
    }

    /**
     * Total units per product across the given orders. Lets a caller (e.g. the sales channel)
     * work out how much stock is tied up in orders it has not finished announcing.
     */
    public Map<String, Integer> unitsInOrders(Collection<Long> orderIds) {
        Map<String, Integer> units = new TreeMap<>();
        if (orderIds.isEmpty()) {
            return units;
        }
        for (Order order : orderRepository.findAllById(orderIds)) {
            for (OrderItem item : order.getItems()) {
                units.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        return units;
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

    // ------------------------------------------------------------------
    // Backorders
    // ------------------------------------------------------------------

    /**
     * Looks at every BACKORDERED order, oldest first. One that can now be
     * filled reserves its stock and becomes CONFIRMED; one that can never be
     * filled (nothing on its way any more, or it waited too long) becomes
     * CANCELLED. Each resolution publishes a BackorderResolvedEvent.
     * Every order is handled in its own transaction.
     */
    public void reevaluateBackorders() {
        for (int i = 0; i < 200 && resolveNextBackorder(); i++) {
            // keep going until nothing more can be resolved
        }
    }

    /**
     * Resolves exactly ONE backorder (the oldest that can be filled or must
     * be cancelled) in its own transaction and returns true, or returns
     * false if every backorder is still legitimately waiting. Callers that
     * need to do something between two resolutions (e.g. tell a marketplace,
     * then publish stock) call this in a loop.
     */
    public boolean resolveNextBackorder() {
        List<Long> waiting = orderRepository.findIdsByStatus(Order.STATUS_BACKORDERED);
        for (Long orderId : waiting) {
            try {
                if (!backorderNeedsAttention(orderId)) {
                    continue; // still waiting for its delivery: take no locks at all
                }
                Boolean changed = requiresNewTx.execute(status -> resolveBackorder(orderId));
                if (Boolean.TRUE.equals(changed)) {
                    return true;
                }
            } catch (RuntimeException e) {
                log.warn("Could not re-evaluate backorder {}: {}", orderId, e.toString());
            }
        }
        return false;
    }

    /**
     * Cheap look, with NO transaction and NO row lock, at whether a backorder could have
     * changed. It must stay outside the transaction in resolveBackorder: an entity loaded
     * earlier in the same transaction would be reused by the locked read below and could be
     * stale, letting two threads fill the same backorder twice.
     */
    private boolean backorderNeedsAttention(Long orderId) {
        Order peek = orderRepository.findById(orderId).orElse(null);
        if (peek == null || !Order.STATUS_BACKORDERED.equals(peek.getStatus())) {
            return false;
        }
        Map<String, Integer> needed = new TreeMap<>();
        for (OrderItem item : peek.getItems()) {
            needed.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        boolean fillable = needed.entrySet().stream()
                .allMatch(e -> inventoryService.getItem(e.getKey()).getStock() >= e.getValue());
        boolean expired = peek.getCreatedAt().plusMinutes(backorderTimeoutMinutes).isBefore(LocalDateTime.now());
        return fillable || expired || !stillCoveredBySupplier(needed);
    }

    /** @return true if the order's status changed (filled or cancelled). */
    private boolean resolveBackorder(Long orderId) {
        // The row lock comes FIRST and is the first time this transaction reads the order,
        // so what we see is always the committed truth.
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !Order.STATUS_BACKORDERED.equals(order.getStatus())) {
            return false; // someone else (a cancellation, another run) got there first
        }

        Map<String, Integer> needed = new TreeMap<>();
        for (OrderItem item : order.getItems()) {
            needed.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        lockInOrder(needed.keySet());

        boolean fillable = needed.entrySet().stream()
                .allMatch(e -> inventoryService.getItem(e.getKey()).getStock() >= e.getValue());

        if (fillable) {
            for (Map.Entry<String, Integer> e : needed.entrySet()) {
                InventoryItem reserved = inventoryService.reserve(e.getKey(), e.getValue());
                if (reserved.getStock() < lowStockThreshold) {
                    onLowStock(reserved);
                }
            }
            order.setStatus(Order.STATUS_CONFIRMED);
            order.setReason(null);
            orderRepository.save(order);

            List<OrderItemEvent> itemEvents = needed.entrySet().stream()
                    .map(e -> new OrderItemEvent(e.getKey(), e.getValue()))
                    .toList();
            eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), itemEvents));
            eventPublisher.publishEvent(new BackorderResolvedEvent(order.getOrderId(), true));
            log.info("Backorder {} filled from new stock", order.getOrderId());
            return true;
        }

        boolean expired = order.getCreatedAt().plusMinutes(backorderTimeoutMinutes).isBefore(LocalDateTime.now());
        if (!expired && stillCoveredBySupplier(needed)) {
            return false; // still waiting for the delivery
        }

        order.setStatus(Order.STATUS_CANCELLED);
        order.setReason(expired ? "Backorder timed out" : "Supplier delivery cannot cover this order");
        orderRepository.save(order);
        eventPublisher.publishEvent(new BackorderResolvedEvent(order.getOrderId(), false));
        log.info("Backorder {} cancelled: {}", order.getOrderId(), order.getReason());
        return true;
    }

    private boolean stillCoveredBySupplier(Map<String, Integer> needed) {
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            int stock = inventoryService.getItem(e.getKey()).getStock();
            if (stock >= e.getValue()) {
                continue;
            }
            boolean coming = supplierGateway.unitsOnOrder(e.getKey()) > 0
                    || supplierGateway.hasUnsubmittedReorder(e.getKey());
            if (!coming) {
                return false;
            }
        }
        return true;
    }

    /** True if every short line will be covered by supplier stock already ordered. */
    private boolean canBackorder(List<LineValidation> validations) {
        for (LineValidation v : validations) {
            if (OUTCOME_OK.equals(v.outcome)) {
                continue;
            }
            if (!OUTCOME_INSUFFICIENT_STOCK.equals(v.outcome)) {
                return false;
            }
            if (!coveredByIncomingStock(v.productId, v.quantity)) {
                return false;
            }
        }
        return true;
    }

    private boolean coveredByIncomingStock(String productId, int quantity) {
        int stock = inventoryService.getItem(productId).getStock();
        int promised = backorderedUnits(productId);
        int future = stock + supplierGateway.unitsOnOrder(productId) - promised;
        // Pure database work on purpose: this runs while inventory rows are locked, so it must
        // never wait on the supplier. Any shortage reorder was placed beforehand by
        // ensureSupplyForShortages(), outside the transaction.
        return future >= quantity;
    }

    private int backorderedUnits(String productId) {
        return (int) orderRepository.sumQuantityByStatusAndProduct(Order.STATUS_BACKORDERED, productId);
    }

    /**
     * Call this BEFORE placeOrderAllowingBackorder, outside any transaction. For every
     * product the order is short of, and that the supplier is not already going to cover,
     * it places a reorder with the supplier. The (possibly slow) supplier call therefore
     * never happens while stock rows are locked, and customers' orders never queue behind it.
     * Never throws for ordinary supplier trouble.
     */
    public void ensureSupplyForShortages(List<OrderItemRequest> requests) {
        for (OrderItemRequest line : mergeLines(requests)) {
            try {
                int stock = inventoryService.getItem(line.getProductId()).getStock();
                if (stock >= line.getQuantity()) {
                    continue;
                }
                int future = stock + supplierGateway.unitsOnOrder(line.getProductId())
                        - backorderedUnits(line.getProductId());
                if (future >= line.getQuantity()) {
                    continue;
                }
                String productId = line.getProductId();
                int wanted = line.getQuantity();
                supplyReorderer.reorderShortage(productId, reorderQuantity, () -> {
                    int stockNow = inventoryService.getItem(productId).getStock();
                    return wanted - (stockNow + supplierGateway.unitsOnOrder(productId) - backorderedUnits(productId));
                });
            } catch (ProductNotFoundException ignored) {
                // unknown product: placeOrderAllowingBackorder will reject it
            } catch (RuntimeException e) {
                log.warn("Could not secure supply for {}: {}", line.getProductId(), e.toString());
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Row-locks the products in sorted order so concurrent orders cannot deadlock or oversell. */
    private void lockInOrder(Collection<String> productIds) {
        productIds.stream().distinct().sorted().forEach(id -> {
            try {
                inventoryService.getItemForUpdate(id);
            } catch (ProductNotFoundException ignored) {
                // validateAll reports unknown products as NOT_FOUND.
            }
        });
    }

    private List<OrderItemRequest> mergeLines(List<OrderItemRequest> requests) {
        Map<String, Integer> merged = new TreeMap<>();
        for (OrderItemRequest r : requests) {
            merged.merge(r.getProductId(), r.getQuantity(), Integer::sum);
        }
        List<OrderItemRequest> lines = new ArrayList<>();
        merged.forEach((productId, quantity) -> lines.add(new OrderItemRequest(productId, quantity)));
        return lines;
    }

    private List<InventorySnapshot> reserveAll(List<LineValidation> validations) {
        List<InventorySnapshot> snapshots = new ArrayList<>();
        for (LineValidation v : validations) {
            InventoryItem reserved = inventoryService.reserve(v.productId, v.quantity);
            snapshots.add(toSnapshot(reserved));
            if (reserved.getStock() < lowStockThreshold) {
                onLowStock(reserved);
            }
        }
        return snapshots;
    }

    /**
     * Lab 3 auto-reorder. Lab 4 adds one guard: if a reorder for this
     * product is already on its way (or queued for resend) do not place
     * another one, otherwise a burst of orders would buy the same stock
     * ten times over.
     */
    private void onLowStock(InventoryItem reserved) {
        // Only announce it. The reorder itself (a slow call to the supplier) is made by
        // LowStockReorderListener AFTER this transaction has committed, so stock rows are
        // never held locked while we wait on LegacySupply.
        eventPublisher.publishEvent(new LowStockEvent(
                reserved.getProductId(), reserved.getName(), reserved.getStock()));
    }

    private void publishPlaced(Long orderId, List<LineValidation> validations) {
        List<OrderItemEvent> itemEvents = validations.stream()
                .map(v -> new OrderItemEvent(v.productId, v.quantity))
                .toList();
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, itemEvents));
    }

    private Order saveRejected(List<LineValidation> validations) {
        Order order = new Order(Order.STATUS_REJECTED, null);
        for (LineValidation v : validations) {
            order.addItem(new OrderItem(v.productId, v.quantity));
        }
        String reason = buildRejectionReason(validations);
        order.setReason(reason);
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
        return order;
    }

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
        String reason = validations.stream()
                .filter(v -> !OUTCOME_OK.equals(v.outcome))
                .map(v -> v.productId + ": " + v.detail)
                .collect(Collectors.joining("; "));
        return reason.length() <= 250 ? reason : reason.substring(0, 250); // orders.reason is varchar(255)
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
