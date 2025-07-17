package au.org.aodn.ogcapi.server.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptModel {
    protected String id;
    protected String url;
    protected String description;
    protected String title;
}
