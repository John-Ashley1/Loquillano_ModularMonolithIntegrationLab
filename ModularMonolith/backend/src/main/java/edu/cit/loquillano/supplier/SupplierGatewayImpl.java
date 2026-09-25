package edu.cit.loquillano.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private, same rule as InventoryServiceImpl: this class is the
 * only thing in the whole codebase that knows what a SupplierSku or a
 * LegacySupply Uom is. Everything it takes in and hands back at its public
 * boundary (SupplierGateway) is in our own terms.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final SupplierCatalogProperties catalogProperties;
    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;

    SupplierGatewayImpl(SupplierCatalogProperties catalogProperties,
                         SupplierOrderRepository supplierOrderRepository,
                         LegacySupplyClient legacySupplyClient) {
        this.catalogProperties = catalogProperties;
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
    }

    @Override
    @Transactional
    public ReorderResult reorder(String productId, int unitsNeeded) {
        SupplierCatalogProperties.Mapping mapping = catalogProperties.forProduct(productId);
        if (mapping == null) {
            log.warn("No LegacySupply catalog mapping configured for product {}", productId);
            return new ReorderResult(null, SupplierOrderStatus.FAILED,
                    "No supplier mapping configured for " + productId);
        }

        // Round up to whole cases - we can't order a fraction of a pack.
        int cases = (int) Math.ceil(unitsNeeded / (double) mapping.getPackSize());
        String requestId = LegacySupplyHttp.newRequestId();

        // Save first (status PENDING) so we have an id to build BuyerRef from,
        // and so the reorder is durable even if the call below never returns.
        SupplierOrder order = new SupplierOrder(productId, requestId, cases, unitsNeeded, SupplierOrderStatus.PENDING);
        order = supplierOrderRepository.save(order);
        order.setBuyerRef("RO-" + order.getId());
        order = supplierOrderRepository.save(order);

        return attemptSubmit(order, mapping.getSku());
    }

    /**
     * Tries to actually submit a PENDING order to LegacySupply. Used both
     * for the first attempt (from reorder()) and for the resend job
     * (Part D) - always reusing the SAME requestId/buyerRef already
     * stored on the row, so a resend can never create a duplicate PO.
     */
    ReorderResult attemptSubmit(SupplierOrder order, String supplierSku) {
        try {
            PurchaseOrderAckXml ack = legacySupplyClient.createPurchaseOrder(
                    supplierSku, order.getCases(), order.getBuyerRef(), order.getRequestId());

            order.setPoNumber(ack.poNumber);
            order.setStatus(mapStatusCode(ack.statusCode));
            supplierOrderRepository.save(order);

            return new ReorderResult(order.getId(), order.getStatus(), "Submitted as " + ack.poNumber);

        } catch (LegacySupplyException e) {
            if (e.retryable) {
                // Leave it PENDING - the resend job (Part D) will pick it up later.
                log.warn("LegacySupply unavailable for order {}, leaving PENDING for resend: {}",
                        order.getId(), e.getMessage());
                return new ReorderResult(order.getId(), SupplierOrderStatus.PENDING, e.getMessage());
            }

            // Non-retryable (e.g. bad SKU/qty) - this reorder will never succeed as-is.
            order.setStatus(SupplierOrderStatus.FAILED);
            supplierOrderRepository.save(order);
            log.error("LegacySupply rejected order {} permanently: {}", order.getId(), e.getMessage());
            return new ReorderResult(order.getId(), SupplierOrderStatus.FAILED, e.getMessage());
        }
    }

    static SupplierOrderStatus mapStatusCode(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}
