package edu.cit.loquillano.notification;

import edu.cit.loquillano.event.LowStockEvent;
import edu.cit.loquillano.event.OrderPlacedEvent;
import edu.cit.loquillano.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for domain events published by the Order/Inventory flow.
 * Package-private, same reasoning as InventoryServiceImpl: nothing outside
 * this package needs to know this class exists. This class imports ONLY
 * from edu.cit.loquillano.event — never InventoryService or OrderService —
 * which is what keeps Notification decoupled from the modules that raise
 * these events.
 *
 * Listeners run synchronously (Spring's default) rather than @Async. For
 * this lab that's deliberate: a synchronous listener runs inside the same
 * transaction/request as the order, so a notification failure would be
 * visible immediately instead of silently vanishing on a background
 * thread, and the four required Network-tab/demo scenarios need the
 * notification row to exist by the time the response comes back and the
 * frontend re-polls GET /api/notifications right after. See the README
 * for the full trade-off discussion.
 */
@Component
class NotificationEventListener {

    private final NotificationRepository notificationRepository;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationRepository.save(new Notification("Order " + event.getOrderId() + " confirmed"));
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        notificationRepository.save(new Notification(
                "Order " + event.getOrderId() + " rejected: " + event.getReason()));
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        notificationRepository.save(new Notification(
                "Reorder needed: " + event.getProductName() + " (" + event.getProductId()
                        + ") is down to " + event.getRemainingStock() + " in stock"));
    }
}
