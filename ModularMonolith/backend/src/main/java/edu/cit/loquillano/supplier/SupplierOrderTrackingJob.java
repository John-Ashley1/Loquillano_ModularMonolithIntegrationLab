package edu.cit.loquillano.supplier;

import edu.cit.loquillano.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Part E: polls LegacySupply for the status of every order we still
 * consider "open" (submitted but not yet delivered/failed), maps their
 * StatusCode to our own SupplierOrderStatus, and - the only interesting
 * transition - publishes SupplierOrderDeliveredEvent the moment an order
 * newly becomes DELIVERED. Inventory restocks off that event; this job
 * never calls Inventory or Order directly.
 */
@Component
class SupplierOrderTrackingJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderTrackingJob.class);

    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.ACCEPTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED,
            SupplierOrderStatus.UNKNOWN);

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderTrackingJob(SupplierOrderRepository supplierOrderRepository,
                              LegacySupplyClient legacySupplyClient,
                              ApplicationEventPublisher eventPublisher) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.supplier.tracking-interval-ms:60000}")
    void trackOpenOrders() {
        List<SupplierOrder> open = supplierOrderRepository.findAllByStatusIn(OPEN_STATUSES);
        if (open.isEmpty()) {
            return;
        }

        for (SupplierOrder order : open) {
            if (order.getPoNumber() == null) {
                continue; // never actually submitted yet - the resend job owns this one
            }
            try {
                PurchaseOrderAckXml status = legacySupplyClient.getPurchaseOrder(order.getPoNumber());
                SupplierOrderStatus mapped = SupplierGatewayImpl.mapStatusCode(status.statusCode);

                if (mapped == SupplierOrderStatus.UNKNOWN) {
                    // An LegacySupply status we don't recognize. See INTEGRATION.md
                    // for how this is handled: logged and left as UNKNOWN rather
                    // than guessed at, so it surfaces for a human to check.
                    log.warn("Unrecognized LegacySupply StatusCode {} for order {}", status.statusCode, order.getId());
                }

                boolean newlyDelivered = mapped == SupplierOrderStatus.DELIVERED
                        && order.getStatus() != SupplierOrderStatus.DELIVERED;

                order.setStatus(mapped);
                supplierOrderRepository.save(order);

                if (newlyDelivered) {
                    eventPublisher.publishEvent(
                            new SupplierOrderDeliveredEvent(order.getProductId(), order.getUnits()));
                    log.info("Supplier order {} delivered - restocking {} units of {}",
                            order.getId(), order.getUnits(), order.getProductId());
                }
            } catch (LegacySupplyException e) {
                log.warn("Could not check status of supplier order {} ({}): {}",
                        order.getId(), order.getPoNumber(), e.getMessage());
                // Leave status as-is; next tick tries again.
            }
        }
    }
}
