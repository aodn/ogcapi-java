package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ThemeModel {
    protected String scheme;
    protected String description;
    protected String title;
    protected List<ConceptModel> concepts;

}
