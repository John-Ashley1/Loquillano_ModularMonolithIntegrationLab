package edu.cit.loquillano.supplier;

/**
 * Our own domain vocabulary for a supplier order's lifecycle. Deliberately
 * NOT a 1:1 mirror of LegacySupply's numeric StatusCode (10/20/30/40) —
 * translating into our own enum is exactly what keeps that detail out of
 * the rest of the system.
 */
public enum SupplierOrderStatus {
    /** Created locally, not yet accepted by LegacySupply (never sent, or send failed and is queued for retry). */
    PENDING,
    /** LegacySupply accepted the order (their StatusCode 10). */
    ACCEPTED,
    /** Being picked (their StatusCode 20). */
    PICKING,
    /** Shipped (their StatusCode 30). */
    SHIPPED,
    /** Delivered (their StatusCode 40) - triggers the restock event. */
    DELIVERED,
    /** We received a status we didn't expect; see INTEGRATION.md for how these are handled. */
    UNKNOWN,
    /** Submission failed in a way retries won't fix (e.g. item/qty rejected). */
    FAILED
}
