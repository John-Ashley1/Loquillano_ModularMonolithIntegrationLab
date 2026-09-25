package edu.cit.loquillano.supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Binds application.properties entries like:
 *   app.supplier.catalog.P100.sku=ABC-1234
 *   app.supplier.catalog.P100.pack-size=12
 * to our own productId -> (SupplierSku, PackSize) mapping. This is the
 * ONE place LegacySupply's item numbers exist in config; nothing outside
 * this module ever sees a SupplierSku.
 *
 * Fill in real values here (and in application-local.properties) after
 * Part B's discovery pass — the mapping table you build for
 * INTEGRATION.md is exactly this config.
 */
@Component
@ConfigurationProperties(prefix = "app.supplier")
class SupplierCatalogProperties {

    private Map<String, Mapping> catalog;

    Map<String, Mapping> getCatalog() {
        return catalog;
    }

    void setCatalog(Map<String, Mapping> catalog) {
        this.catalog = catalog;
    }

    Mapping forProduct(String productId) {
        if (catalog == null) {
            return null;
        }
        return catalog.get(productId);
    }

    static class Mapping {
        private String sku;
        private int packSize;

        String getSku() {
            return sku;
        }

        void setSku(String sku) {
            this.sku = sku;
        }

        int getPackSize() {
            return packSize;
        }

        void setPackSize(int packSize) {
            this.packSize = packSize;
        }
    }
}
