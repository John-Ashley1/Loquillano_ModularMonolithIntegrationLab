package edu.cit.loquillano.channel;

import edu.cit.loquillano.config.AppInstance;
import edu.cit.loquillano.inventory.InventoryItem;
import edu.cit.loquillano.inventory.InventoryService;
import edu.cit.loquillano.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tasks 1 and 2. When the app has started: heartbeat first (before any
 * other Tiangge call), then publish the listings, then publish stock.
 * Afterwards a heartbeat goes out every 30 seconds for as long as the app
 * runs. If Tiangge is unreachable at startup, every 30-second tick simply
 * tries again, so the app goes live by itself as soon as Tiangge is back.
 */
@Component
class ChannelLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChannelLifecycle.class);
    private static final int MAX_LISTINGS = 10;

    private final AppInstance instance;
    private final TianggeClient client;
    private final ChannelState state;
    private final StockPublisher stockPublisher;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final String appName;

    ChannelLifecycle(AppInstance instance, TianggeClient client, ChannelState state,
                     StockPublisher stockPublisher, InventoryService inventoryService,
                     SupplierGateway supplierGateway,
                     @Value("${app.channel.app-name:loquillano-shop}") String appName) {
        this.instance = instance;
        this.client = client;
        this.state = state;
        this.stockPublisher = stockPublisher;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.appName = appName;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStarted() {
        log.info("Going live on Tiangge with instance {}", instance.getId());
        tick();
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    void heartbeatTick() {
        tick();
    }

    /** Until the shop is live, retry quickly instead of waiting a full 30s (Tiangge may be slow to wake up). */
    @Scheduled(fixedDelay = 5_000, initialDelay = 8_000)
    void retryUntilLive() {
        if (!state.isLive()) {
            tick();
        }
    }

    private synchronized void tick() {
        try {
            long uptime = Duration.between(instance.getStartedAt(), Instant.now()).getSeconds();
            client.heartbeat(appName, instance.getStartedAt(), uptime);
            log.debug("Heartbeat sent (uptime {}s)", uptime);
        } catch (RuntimeException e) {
            log.warn("Heartbeat failed, will try again in 30s: {}", e.toString());
            return;
        }

        if (!state.isLive()) {
            try {
                publishListings();
            } catch (RuntimeException e) {
                log.warn("Publishing listings failed, will try again on the next heartbeat: {}", e.toString());
            }
        }
    }

    private void publishListings() {
        List<TianggeClient.Listing> listings = new ArrayList<>();
        Set<String> productIds = new LinkedHashSet<>();
        for (InventoryItem item : inventoryService.getAllItems()) {
            supplierGateway.supplierSkuFor(item.getProductId()).ifPresent(sku -> {
                if (listings.size() < MAX_LISTINGS) {
                    listings.add(new TianggeClient.Listing(item.getProductId(), item.getName(), sku));
                    productIds.add(item.getProductId());
                }
            });
        }
        if (listings.isEmpty()) {
            log.error("No products have a supplier mapping, so there is nothing to list on Tiangge");
            return;
        }

        client.putListings(listings);
        state.markLive(productIds);
        log.info("Published {} listing(s) on Tiangge: {}", listings.size(), productIds);

        // Tiangge only shows listings that have stock information.
        stockPublisher.markDirty();
    }
}
