package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "LSError")
@JsonIgnoreProperties(ignoreUnknown = true)
class LSErrorXml {

    @JacksonXmlProperty(localName = "Code")
    String code;

    @JacksonXmlProperty(localName = "Message")
    String message;
}
