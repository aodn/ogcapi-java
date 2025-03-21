package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
@Getter
@Setter
public class EsPointModel {
    protected String type;
    protected List<BigDecimal> coordinates;

    public PointGeoJSON toPointGeoJSON() {
        PointGeoJSON p = new PointGeoJSON();
        p.setType(PointGeoJSON.TypeEnum.POINT);
        p.setCoordinates(coordinates);
        return p;
    }
}
