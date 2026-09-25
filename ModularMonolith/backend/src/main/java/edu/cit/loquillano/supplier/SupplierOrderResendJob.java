package edu.cit.loquillano.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Part D: "never lose a reorder." Anything still PENDING (never
 * successfully submitted, or LegacySupply was down when we tried) gets
 * retried here on a timer, reusing its stored requestId/buyerRef every
 * time - so however many times this job picks up the same row, LegacySupply
 * can never see it as two different orders.
 */
@Component
class SupplierOrderResendJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderResendJob.class);

    private final SupplierOrderRepository supplierOrderRepository;
    private final SupplierGatewayImpl supplierGateway;
    private final SupplierCatalogProperties catalogProperties;

    SupplierOrderResendJob(SupplierOrderRepository supplierOrderRepository,
                            SupplierGatewayImpl supplierGateway,
                            SupplierCatalogProperties catalogProperties) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.supplierGateway = supplierGateway;
        this.catalogProperties = catalogProperties;
    }

    @Scheduled(fixedDelayString = "${app.supplier.resend-interval-ms:30000}")
    void resendPendingOrders() {
        List<SupplierOrder> pending = supplierOrderRepository.findAllByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        log.info("Resending {} pending supplier order(s)", pending.size());
        for (SupplierOrder order : pending) {
            SupplierCatalogProperties.Mapping mapping = catalogProperties.forProduct(order.getProductId());
            if (mapping == null) {
                log.warn("Skipping resend for order {} - no catalog mapping for {}", order.getId(), order.getProductId());
                continue;
            }
            supplierGateway.attemptSubmit(order, mapping.getSku());
        }
    }
}
