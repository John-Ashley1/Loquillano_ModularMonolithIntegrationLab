package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/** Package-private: LegacySupply's wire format. Never leaves this package. */
@JacksonXmlRootElement(localName = "AuthRequest")
class AuthRequestXml {

    @JacksonXmlProperty(localName = "ClientId")
    String clientId;

    @JacksonXmlProperty(localName = "ApiKey")
    String apiKey;

    AuthRequestXml() {
    }

    AuthRequestXml(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }
}
