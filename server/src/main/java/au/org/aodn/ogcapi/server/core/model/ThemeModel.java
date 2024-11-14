package au.org.aodn.ogcapi.server.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeModel {
    protected String scheme;
    protected String description;
    protected String title;
    protected List<ConceptModel> concepts;

}
