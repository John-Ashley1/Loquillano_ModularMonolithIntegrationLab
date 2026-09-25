package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthResponse")
@JsonIgnoreProperties(ignoreUnknown = true)
class AuthResponseXml {

    @JacksonXmlProperty(localName = "SessionToken")
    String sessionToken;

    @JacksonXmlProperty(localName = "IssuedAt")
    String issuedAt;
}
