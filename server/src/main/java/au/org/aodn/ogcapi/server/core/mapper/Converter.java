package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.*;
import au.org.aodn.ogcapi.server.core.model.CitationModel;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import au.org.aodn.ogcapi.server.core.parser.stac.GeometryVisitor;
import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Map;

import static au.org.aodn.ogcapi.server.core.util.GeometryUtils.createCentroid;

@FunctionalInterface
public interface Converter<F, T> {

    Logger logger = LoggerFactory.getLogger(Converter.class);
    GeometryVisitor visitor = GeometryVisitor.builder()
            .build();

    Map<String, String> uuidToNotebookName = Map.ofEntries(
            Map.entry("4402cb50-e20a-44ee-93e6-4728259250d2", "argo.ipynb"),
            Map.entry("af5d0ff9-bb9c-4b7c-a63c-854a630b6984", "autonomous_underwater_vehicle.ipynb"),
            Map.entry("0c9eb39c-9cbe-4c6a-8a10-5867087e703a", "model_sea_level_anomaly_gridded_realtime.ipynb"),
            Map.entry("efd8201c-1eca-412e-9ad2-0534e96cea14", "mooring_hourly_timeseries_delayed_qc.ipynb"),
            Map.entry("78d588ed-79dd-47e2-b806-d39025194e7e", "mooring_satellite_altimetry_calibration_validation.ipynb"),
            Map.entry("7e13b5f3-4a70-4e31-9e95-335efa491c5c", "mooring_temperature_logger_delayed_qc.ipynb"),
            Map.entry("38dd003d-2f71-4715-bd3f-4b1cfdce391d", "radar_BonneyCoast_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("8a2d2824-0557-4110-a561-01ec35a9583d", "radar_CapricornBunkerGroup_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("400a1237-af4d-45c6-a292-788cf0212522", "radar_CapricornBunkerGroup_wave_delayed_qc.ipynb"),
            Map.entry("742dc902-b300-4e3e-839f-04d03671aa09", "radar_CapricornBunkerGroup_wind_delayed_qc.ipynb"),
            Map.entry("85da1645-2c63-45fa-97b5-4125165b999d", "radar_CoffsHarbour_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("e32e51d9-b0a5-4b95-9906-44e0c6c8d516", "radar_CoffsHarbour_wave_delayed_qc.ipynb"),
            Map.entry("ffe8f19c-de4a-4362-89be-7605b2dd6b8c", "radar_CoffsHarbour_wind_delayed_qc.ipynb"),
            Map.entry("f7b36a1c-0936-4da6-b47f-94ed538b367e", "radar_CoralCoast_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("6dca1f8a-8337-4551-ac4b-a2d35ec6f333", "radar_Newcastle_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("23c27e4f-c982-44e9-9ab7-71094d297549", "radar_NorthWestShelf_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("028b9801-279f-427c-964b-0ffcdf310b59", "radar_RottnestShelf_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("9c6d6a0c-4983-4cb5-b119-02c11ce6af4e", "radar_RottnestShelf_wave_delayed_qc.ipynb"),
            Map.entry("cb2e22b5-ebb9-460b-8cff-b446fe14ea2f", "radar_SouthAustraliaGulfs_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("19da2ce7-138f-4427-89de-a50c724f5f54", "radar_SouthAustraliaGulfs_wave_delayed_qc.ipynb"),
            Map.entry("055342fc-f970-4be7-a764-8903220d42fb", "radar_TurquoiseCoast_velocity_hourly_averaged_delayed_qc.ipynb"),
            Map.entry("541d4f15-122a-443d-ab4e-2b5feb08d6a0", "receiver_animal_acoustic_tagging_delayed_qc.ipynb"),
            Map.entry("43ac4663-c8de-4eb0-9711-3da65cbecdd3", "satellite_chlorophylla_carder_1day_aqua.ipynb"),
            Map.entry("f73daf07-eb81-4995-a72a-ca903834509f", "satellite_chlorophylla_gsm_1day_aqua.ipynb"),
            Map.entry("d7a14921-8f3f-4522-9a54-e7d1df969c8a", "satellite_chlorophylla_oc3_1day_aqua.ipynb"),
            Map.entry("24055e3a-94e5-40bb-b97f-7519f0482d6a", "satellite_chlorophylla_oci_1day_aqua.ipynb"),
            Map.entry("a8632154-b8e5-493d-acd4-e458fae3ae26", "satellite_diffuse_attenuation_coefficent_1day_aqua.ipynb"),
            Map.entry("72b65fb8-84e1-4a56-b32c-7f15970903d2", "satellite_ghrsst_l3c_1day_nighttime_himawari8.ipynb")
    );

    @Builder
    @Getter
    @Setter
    class Param {
        CQLCrsType coordinationSystem;
        String filter;
    }

    T convert(F from, Filter param);

    default au.org.aodn.ogcapi.features.model.Link getSelfCollectionLink(String hostname, String id) {
        au.org.aodn.ogcapi.features.model.Link self = new au.org.aodn.ogcapi.features.model.Link();

        self.rel("self");
        self.type(MediaType.APPLICATION_JSON_VALUE);
        self.href(String.format("%s/collections/%s",hostname, id));

        return self;
    }

    default au.org.aodn.ogcapi.tile.model.Link getSelfTileLink(String hostname, String id) {
        au.org.aodn.ogcapi.tile.model.Link self = new au.org.aodn.ogcapi.tile.model.Link();

        self.rel("self");
        self.type(MediaType.APPLICATION_JSON_VALUE);
        self.href(String.format("%s/collections/%s/tiles/WebMercatorQuad",hostname, id));

        return self;
    }

    default au.org.aodn.ogcapi.tile.model.Link getTileSchema(String hostname) {
        au.org.aodn.ogcapi.tile.model.Link schema = new au.org.aodn.ogcapi.tile.model.Link();
        schema.rel("http://www.opengis.net/def/rel/ogc/1.0/tiling-scheme");
        schema.type(MediaType.APPLICATION_JSON_VALUE);
        schema.href(String.format("%s/tileMatrixSets/WebMercatorQuad",hostname));

        return schema;
    }
    /**
     * Create a collection given stac model
     * @param m - Income object to be transformed
     * @return A mapped JSON which match the St
     */
    default <D extends StacCollectionModel> Collection getCollection(D m, Filter filter, String host) {

        ExtendedCollection collection = new ExtendedCollection();
        collection.setId(m.getUuid());
        collection.setTitle(m.getTitle());
        collection.setDescription(m.getDescription());
        collection.setItemType("Collection");

        Extent extent = new Extent();

        if(m.getExtent() != null) {
            extent.setSpatial(new ExtentSpatial());

            if(m.getExtent().getBbox() != null
                    && !m.getExtent().getBbox().isEmpty()) {
                // The first item is the overall bbox, this is STAC spec requirement and it ok with ogc api
                extent.getSpatial().bbox(m.getExtent().getBbox());
                collection.setExtent(extent);
            }
            else {
                logger.warn("BBOX is missing for this UUID {}", m.getUuid());
            }

            extent.setTemporal(new ExtentTemporal());
            extent.getTemporal().interval(m.getExtent().getTemporal());
            collection.setExtent(extent);
        }

        if(m.getLinks() != null || (m.getUuid() != null && uuidToNotebookName.containsKey(m.getUuid())) || m.getAssets() != null) {
            collection.setLinks(new ArrayList<>());

            // Convert object type.
            if(m.getLinks() != null) {
                collection.getLinks().addAll(
                        m.getLinks()
                                .stream()
                                .map(l -> new Link()
                                        .href(l.getHref())
                                        .type(l.getType())
                                        .rel(l.getRel())
                                        .title(l.getTitle())
                                )
                                .toList()
                );
            }

            if(m.getAssets() != null) {
                m.getAssets().values().forEach(i -> collection.getLinks().add(new Link()
                        .title(i.getTitle())
                        .href(host + "/api/v1/ogc" + i.getHref())
                        .type(i.getType())
                        .rel(i.getRole().toString().toLowerCase())
                ));
            }

            // TODO: This is temp workaround for demo purpose, we need a way to map the notebook name to uuid
            if(m.getUuid() != null) {
                collection.getLinks().add(new Link()
                        .href("https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/" + uuidToNotebookName.get(m.getUuid()))
                        .type(MediaType.APPLICATION_JSON_VALUE)
                        .rel("related")
                        .title("Python Notebook Example"));
            }
        }

        if (m.getContacts() != null) {
            collection.getProperties().put(CollectionProperty.contacts, m.getContacts());
        }

        if (m.getThemes() != null) {
            collection.getProperties().put(CollectionProperty.themes, m.getThemes());
        }

        if(m.getCitation() != null && !m.getCitation().isEmpty()) {
            ConstructUtils.constructByJsonString(m.getCitation(), CitationModel.class).ifPresent(
                    citation -> collection.getProperties().put(CollectionProperty.citation, citation)
            );
        }

        if (m.getLicense() != null) {
            collection.getProperties().put(CollectionProperty.license, m.getLicense());
        }

        if(m.getSummaries() != null ) {
            Map<?, ?> noLand = m.getSummaries().getGeometryNoLand();
            if (noLand != null) {
                // Geometry from elastic search always store in EPSG4326
                GeometryUtils.readGeometry(noLand)
                        .ifPresent(input -> {
                            Geometry g = filter != null ? (Geometry)filter.accept(visitor, input) : input;
                            collection.getProperties().put(
                                    CollectionProperty.centroid,
                                    createCentroid(g)
                            );
                        });
            }

            if (m.getSummaries().getStatus() != null) {
                collection.getProperties().put(CollectionProperty.status, m.getSummaries().getStatus());
            }

            if (m.getSummaries().getCredits() != null) {
                collection.getProperties().put(CollectionProperty.credits, m.getSummaries().getCredits());
            }

            if (m.getSummaries().getGeometry() != null) {
                collection.getProperties().put(CollectionProperty.geometry, m.getSummaries().getGeometry());
            }

            if (m.getSummaries().getTemporal() != null) {
                collection.getProperties().put(CollectionProperty.temporal, m.getSummaries().getTemporal());
            }

            if (m.getSummaries().getStatement() != null) {
                collection.getProperties().put(CollectionProperty.statement, m.getSummaries().getStatement());
            }

            if (m.getSummaries().getCreation() != null) {
                collection.getProperties().put(CollectionProperty.creation, m.getSummaries().getCreation());
            }

            if (m.getSummaries().getRevision() != null) {
                collection.getProperties().put(CollectionProperty.revision, m.getSummaries().getRevision());
            }
        }

        return collection;
    }
}
