package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FeatureTypeInfo {

    protected String name;
    protected String title;
}
