package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeywordList {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Keyword")
    private List<String> keyword;
}
