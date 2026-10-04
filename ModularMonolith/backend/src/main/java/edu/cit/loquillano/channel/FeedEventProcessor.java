package edu.cit.loquillano.channel;

import edu.cit.loquillano.shop.OrderService;
import edu.cit.loquillano.shop.dto.OrderItemRequest;
import edu.cit.loquillano.shop.dto.PlacementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one feed event into work in OUR system, inside ONE database
 * transaction: the real order (through the same OrderService the React UI
 * uses), the ChannelOrder link row, and the "event handled" marker either
 * all commit together or none do. That is what makes processing
 * exactly-once even if the app dies half way.
 *
 * Each method returns the Tiangge order id that now has something to send
 * to Tiangge, or null if there is nothing to send.
 */
@Component
class FeedEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(FeedEventProcessor.class);

    private final OrderService orderService;
    private final ChannelOrderRepository channelOrders;
    private final ProcessedFeedEventRepository processedEvents;

    FeedEventProcessor(OrderService orderService, ChannelOrderRepository channelOrders,
                       ProcessedFeedEventRepository processedEvents) {
        this.orderService = orderService;
        this.channelOrders = channelOrders;
        this.processedEvents = processedEvents;
    }

    /**
     * Step 1, deliberately NOT in a transaction: if the order is short of something and the
     * supplier is not already covering it, place the reorder now, so the slow supplier call
     * never happens while stock rows are locked in process().
     */
    public void prepare(TianggeClient.FeedEvent event) {
        if (!"ORDER_PLACED".equals(event.type())
                || processedEvents.existsById(event.eventId())
                || channelOrders.existsById(event.orderId())) {
            return;
        }
        try {
            orderService.ensureSupplyForShortages(toItems(event));
        } catch (RuntimeException e) {
            log.warn("Could not prepare supply for Tiangge order {}: {}", event.orderId(), e.toString());
        }
    }

    /** Step 2: dispatches by event type, in one transaction. Unknown types are ignored. */
    @Transactional
    public String process(TianggeClient.FeedEvent event) {
        return switch (event.type()) {
            case "ORDER_PLACED" -> onOrderPlaced(event);
            case "ORDER_CANCELLED" -> onOrderCancelled(event);
            default -> {
                log.debug("Ignoring feed event {} of type {}", event.eventId(), event.type());
                yield null;
            }
        };
    }

    private String onOrderPlaced(TianggeClient.FeedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Feed event {} for order {} already processed (redelivery), skipping",
                    event.eventId(), event.orderId());
            return null;
        }

        String sendOrderId = null;
        if (channelOrders.existsById(event.orderId())) {
            // Same Tiangge order delivered again under a new eventId: still ONE order in our system.
            log.info("Tiangge order {} already became a shop order (redelivery), skipping", event.orderId());
        } else {
            PlacementResult result = orderService.placeOrderAllowingBackorder(toItems(event));
            String decision = decisionFor(result.status());
            channelOrders.save(new ChannelOrder(event.orderId(), result.orderId(), decision));
            sendOrderId = event.orderId();
            log.info("Tiangge order {} -> shop order {} ({}), decide by {}",
                    event.orderId(), result.orderId(), decision, event.decisionDeadline());
        }

        processedEvents.save(new ProcessedFeedEvent(event.eventId(), event.type(), event.orderId(), event.seq()));
        return sendOrderId;
    }

    private String onOrderCancelled(TianggeClient.FeedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Cancellation event {} already processed (redelivery), skipping", event.eventId());
            return null;
        }

        ChannelOrder link = channelOrders.findById(event.orderId()).orElse(null);
        String sendOrderId = null;
        if (link == null) {
            log.warn("Cancellation for Tiangge order {} which we never turned into a shop order; ignoring",
                    event.orderId());
        } else if (!link.isCancelRequested()) {
            // Check first instead of catching an exception: an exception thrown through
            // OrderService's transaction would mark this whole transaction rollback-only.
            String status = orderService.getStatus(link.getShopOrderId());
            if (!"CANCELLED".equals(status)) {
                orderService.cancelOrder(link.getShopOrderId()); // restocks Inventory (Lab 2 logic)
            }
            link.requestCancelConfirmation();
            channelOrders.save(link);
            sendOrderId = event.orderId();
            log.info("Tiangge order {} cancelled by customer -> shop order {} cancelled and restocked, confirm by {}",
                    event.orderId(), link.getShopOrderId(), event.confirmDeadline());
        } else {
            sendOrderId = event.orderId(); // already cancelled; make sure the confirmation goes out
        }

        processedEvents.save(new ProcessedFeedEvent(event.eventId(), event.type(), event.orderId(), event.seq()));
        return sendOrderId;
    }

    // --- translators: Tiangge's words <-> ours ------------------------------

    private static List<OrderItemRequest> toItems(TianggeClient.FeedEvent event) {
        List<OrderItemRequest> items = new ArrayList<>();
        for (TianggeClient.Line line : event.lines()) {
            if (line.qty() > 0) {
                items.add(new OrderItemRequest(line.sellerSku(), line.qty())); // sellerSku IS our productId
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order " + event.orderId() + " has no usable lines");
        }
        return items;
    }

    private static String decisionFor(String orderStatus) {
        return switch (orderStatus) {
            case "CONFIRMED" -> "ACCEPTED";
            case "BACKORDERED" -> "BACKORDERED";
            default -> "REJECTED";
        };
    }
}
