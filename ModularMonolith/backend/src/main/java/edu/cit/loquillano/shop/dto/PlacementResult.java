package edu.cit.loquillano.shop.dto;

/**
 * Outcome of placing an order that is allowed to wait for stock.
 * status is one of the Order.STATUS_* values: CONFIRMED, REJECTED or BACKORDERED.
 */
public record PlacementResult(Long orderId, String status, String reason) {
}
