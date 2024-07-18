package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LicenseModel {

    protected String title;
    protected String url;
    protected String licenseGraphic;

}
