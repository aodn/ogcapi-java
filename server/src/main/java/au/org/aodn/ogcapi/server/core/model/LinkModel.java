package au.org.aodn.ogcapi.server.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkModel {
    protected String rel;
    protected String href;
    protected String type;
    protected String title;
}
