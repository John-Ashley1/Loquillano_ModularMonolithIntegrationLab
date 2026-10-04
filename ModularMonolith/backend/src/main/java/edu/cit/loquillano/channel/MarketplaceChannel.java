package edu.cit.loquillano.channel;

/**
 * The ONLY public type in the channel module. Everything else here (HTTP
 * client, JSON mapping, feed poller, translators, entities) is
 * package-private. Order and Inventory never import this package at all;
 * it exists so other code (a status endpoint, a test) can ask "is my shop
 * live on the marketplace right now?".
 */
public interface MarketplaceChannel {

    /** True once the shop has heartbeated and published its listings. */
    boolean isLive();

    /** The UUID of this running copy of the application. */
    String instanceId();
}
