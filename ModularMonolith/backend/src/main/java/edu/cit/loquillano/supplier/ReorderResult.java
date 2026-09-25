package edu.cit.loquillano.supplier;

/**
 * What SupplierGateway.reorder() hands back to the caller. Carries our
 * own supplier_orders id and status — never a LegacySupply PoNumber or
 * StatusCode directly.
 */
public class ReorderResult {

    private final Long supplierOrderId;
    private final SupplierOrderStatus status;
    private final String message;

    public ReorderResult(Long supplierOrderId, SupplierOrderStatus status, String message) {
        this.supplierOrderId = supplierOrderId;
        this.status = status;
        this.message = message;
    }

    public Long getSupplierOrderId() {
        return supplierOrderId;
    }

    public SupplierOrderStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAccepted() {
        return status == SupplierOrderStatus.ACCEPTED || status == SupplierOrderStatus.PENDING;
    }
}
