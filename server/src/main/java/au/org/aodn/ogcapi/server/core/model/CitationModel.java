package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CitationModel {

    protected String suggestedCitation;
    protected List<String> useLimitations;
    protected List<String> otherConstraints;

}
