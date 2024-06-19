package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConceptModel {
    protected String id;
    protected String url;

    public ConceptModel(String id, String url) {
        this.id = id;
        this.url = url;
    }
}
