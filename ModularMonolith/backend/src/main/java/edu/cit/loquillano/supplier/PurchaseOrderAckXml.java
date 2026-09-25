package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Covers both PurchaseOrderAck (from POST /purchase-orders) and
 * PurchaseOrderStatus (from GET /purchase-orders/{PoNumber}) — the manual
 * says the latter has the same fields plus CheckedAt, so one class with an
 * optional field covers both root element names via two annotated
 * subclasses would be cleaner, but Jackson XML root-element matching on
 * deserialize doesn't care about this class's own @JacksonXmlRootElement
 * name, only the field mapping - so one shape is enough here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class PurchaseOrderAckXml {

    @JacksonXmlProperty(localName = "PoNumber")
    String poNumber;

    @JacksonXmlProperty(localName = "StatusCode")
    int statusCode;

    @JacksonXmlProperty(localName = "SupplierSku")
    String supplierSku;

    @JacksonXmlProperty(localName = "Qty")
    int qty;

    @JacksonXmlProperty(localName = "Uom")
    String uom;

    @JacksonXmlProperty(localName = "BuyerRef")
    String buyerRef;

    @JacksonXmlProperty(localName = "CreatedAt")
    String createdAt;

    @JacksonXmlProperty(localName = "CheckedAt")
    String checkedAt;
}
