package edu.cit.loquillano.supplier;

/**
 * Anti-Corruption Layer boundary for LegacySupply. This interface and the
 * plain domain types it uses (ReorderResult, SupplierOrderStatus) are the
 * ONLY public surface of this module. Everything that describes
 * LegacySupply itself — its XML shapes, SupplierSku/PackSize/UnitCost,
 * its numeric StatusCodes, session tokens, HTTP details — stays
 * package-private inside edu.cit.loquillano.supplier and is translated
 * to/from our own terms at this boundary.
 */
public interface SupplierGateway {

    /**
     * Requests a reorder of the given product in our own units. The
     * implementation is responsible for mapping our productId to
     * LegacySupply's SupplierSku, converting units to their pack-based
     * Uom (rounding up), and handling session/timeout/retry concerns
     * internally. Never throws for ordinary supplier failures — those
     * come back as a FAILED or PENDING ReorderResult instead, so a slow
     * or flaky supplier never breaks the caller's own transaction.
     */
    ReorderResult reorder(String productId, int unitsNeeded);

    /**
     * Units (our own units) of this product that LegacySupply has already
     * accepted and not yet delivered. Zero means nothing is on its way.
     * Used to decide whether a customer order may be BACKORDERED.
     */
    int unitsOnOrder(String productId);

    /**
     * True if a reorder for this product has been recorded locally but not
     * yet accepted by LegacySupply (supplier was down; the resend job will
     * retry it). Lets callers avoid piling up duplicate reorders.
     */
    boolean hasUnsubmittedReorder(String productId);

    /**
     * The one sanctioned exit for a SupplierSku: a marketplace listing must
     * name the supplier item it is restocked from. Empty if this product
     * has no supplier mapping.
     */
    java.util.Optional<String> supplierSkuFor(String productId);
}
