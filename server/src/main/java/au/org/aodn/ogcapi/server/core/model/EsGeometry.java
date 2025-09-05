package au.org.aodn.ogcapi.server.core.model;


import au.org.aodn.ogcapi.features.model.GeometryGeoJSON;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EsPointModel.class, name = "Point"),
        @JsonSubTypes.Type(value = EsPolygonModel.class, name = "Polygon")
})
public interface EsGeometry {

    public GeometryGeoJSON toGeoJson();
}
