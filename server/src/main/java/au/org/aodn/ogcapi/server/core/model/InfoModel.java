package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//https://github.com/stac-extensions/contacts?tab=readme-ov-file#info-object
@Data
@Builder
public class InfoModel {
    protected String value;
    protected List<String> roles;
}
