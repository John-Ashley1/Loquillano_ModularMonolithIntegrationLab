package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrder")
class PurchaseOrderRequestXml {

    @JacksonXmlProperty(localName = "SupplierSku")
    String supplierSku;

    @JacksonXmlProperty(localName = "Qty")
    int qty;

    @JacksonXmlProperty(localName = "BuyerRef")
    String buyerRef;

    PurchaseOrderRequestXml() {
    }

    PurchaseOrderRequestXml(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.buyerRef = buyerRef;
    }
}
