package edu.cit.loquillano.channel;

import edu.cit.loquillano.config.AppInstance;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Shared, thread-safe view of "is the shop live, and which products are listed". */
@Component
class ChannelState implements MarketplaceChannel {

    private final AppInstance instance;
    private volatile boolean live;
    private volatile Set<String> listed = Set.of();

    ChannelState(AppInstance instance) {
        this.instance = instance;
    }

    @Override
    public boolean isLive() {
        return live;
    }

    @Override
    public String instanceId() {
        return instance.getId();
    }

    void markLive(Set<String> listedProducts) {
        this.listed = Set.copyOf(listedProducts);
        this.live = true;
    }

    Set<String> listedProducts() {
        return listed;
    }

    boolean isListed(String productId) {
        return listed.contains(productId);
    }
}
