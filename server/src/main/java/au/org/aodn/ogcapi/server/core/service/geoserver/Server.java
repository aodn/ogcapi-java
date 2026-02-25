package au.org.aodn.ogcapi.server.core.service.geoserver;

import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class Server {
    protected WfsServer wfsServer;

    public Server(WfsServer wfsServer) {
        this.wfsServer = wfsServer;
    }
    /**
     * Build CQL filter for temporal and spatial constraints
     */
    protected String buildCqlFilter(String wfsServerUrl, String uuid, String layerName, String sd, String ed, Object multiPolygon) {

        WfsFields wfsFieldModel = wfsServer.getDownloadableFields(
                uuid,
                WfsServer.WfsFeatureRequest.builder()
                        .layerName(layerName)
                        .server(wfsServerUrl)
                        .build()
        );
        log.debug("WFSFieldModel by wfs typename: {}", wfsFieldModel);

        // Validate start and end dates
        final String startDate = DatetimeUtils.validateAndFormatDate(sd, true);
        final String endDate = DatetimeUtils.validateAndFormatDate(ed, false);

        StringBuilder cqlFilter = new StringBuilder();

        if (wfsFieldModel == null || wfsFieldModel.getFields() == null) {
            return cqlFilter.toString();
        }

        List<WfsField> fields = wfsFieldModel.getFields();

        // Possible to have multiple days, better to consider all
        List<WfsField> temporalField = fields.stream()
                .filter(field -> "dateTime".equals(field.getType()) || "date".equals(field.getType()))
                .toList();

        // Add temporal filter only if both dates are specified
        if (!temporalField.isEmpty() && startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            List<String> cqls = new ArrayList<>();
            temporalField.forEach(temp ->
                    cqls.add(String.format("(%s DURING %sT00:00:00Z/%sT23:59:59Z)", temp.getName(), startDate, endDate))
            );
            cqlFilter.append("(").append(String.join(" OR ", cqls)).append(")");
        }

        // Find geometry field
        Optional<WfsField> geometryField = fields.stream()
                .filter(field -> "geometrypropertytype".equalsIgnoreCase(field.getType()))
                .findFirst();

        // Add spatial filter
        if (geometryField.isPresent() && multiPolygon != null) {
            String fieldName = geometryField.get().getName();

            String wkt = GeometryUtils.convertToWkt(multiPolygon);

            if ((wkt != null) && !cqlFilter.isEmpty()) {
                cqlFilter.append(" AND ");
            }

            if (wkt != null) {
                cqlFilter.append("INTERSECTS(")
                        .append(fieldName)
                        .append(",")
                        .append(wkt)
                        .append(")");
            }
        }

        return cqlFilter.toString();
    }
}
