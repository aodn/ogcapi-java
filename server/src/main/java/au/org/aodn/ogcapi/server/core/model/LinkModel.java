package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkModel {
    protected String rel;
    protected String href;
    protected String type;
    protected String title;
}
