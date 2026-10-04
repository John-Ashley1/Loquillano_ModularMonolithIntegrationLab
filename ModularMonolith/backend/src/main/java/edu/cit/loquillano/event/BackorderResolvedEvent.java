package edu.cit.loquillano.event;

/**
 * Published by the Order module when a BACKORDERED order is finally
 * resolved: either it could be filled (accepted = true, stock reserved) or
 * it never can be (accepted = false, order cancelled).
 */
public class BackorderResolvedEvent {

    private final Long orderId;
    private final boolean accepted;

    public BackorderResolvedEvent(Long orderId, boolean accepted) {
        this.orderId = orderId;
        this.accepted = accepted;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
