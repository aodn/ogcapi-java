package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the subset-filter parameters shared by the dataset download batch job
 * and the cloud-optimised size estimate.
 */
public final class SubsetParametersUtils {

    private SubsetParametersUtils() {
    }

    /**
     * Construct the common subset filters (uuid, key, date range, multi_polygon,
     * output format) as the flat string map the batch job submits and the estimate
     * forwards to DAS.
     *
     * @param polygons GeoJSON MultiPolygon object, or the literal
     *                 {@link GeometryUtils#NON_SPECIFIED_MULTIPOLYGON} when the user
     *                 drew no polygon (required)
     */
    public static Map<String, String> buildSubsetParameters(
            ObjectMapper objectMapper,
            String uuid,
            String key,
            String startDate,
            String endDate,
            Object polygons,
            String outputFormat) throws JsonProcessingException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put(DatasetDownloadEnums.Parameter.UUID.getValue(), uuid);
        parameters.put(DatasetDownloadEnums.Parameter.KEY.getValue(), key);
        // A missing date range is expressed with the "non-specified" sentinel rather
        // than null/empty, so DAS (and AWS Batch, which rejects null parameters)
        // always receive an explicit value regardless of what the frontend sends.
        parameters.put(DatasetDownloadEnums.Parameter.START_DATE.getValue(), orNonSpecified(startDate));
        parameters.put(DatasetDownloadEnums.Parameter.END_DATE.getValue(), orNonSpecified(endDate));
        if (outputFormat != null) {
            parameters.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), outputFormat);
        }
        putMultiPolygon(parameters, polygons, objectMapper);
        return parameters;
    }

    private static String orNonSpecified(String date) {
        return (date == null || date.trim().isEmpty()) ? DatetimeUtils.NON_SPECIFIED_DATE : date;
    }

    /**
     * multi_polygon is required: the frontend sends the literal "non-specified"
     * when the user draws no polygon. A String (e.g. "non-specified") does not
     * round-trip through {@link ObjectMapper#writeValueAsString} cleanly, so it is
     * handled separately from a GeoJSON object.
     */
    private static void putMultiPolygon(Map<String, String> parameters, Object polygons, ObjectMapper objectMapper)
            throws JsonProcessingException {
        if (polygons == null || polygons.toString().isEmpty()) {
            throw new IllegalArgumentException(
                    "Polygons parameter should not be null. If users didn't specify polygons, a 'non-specified' should be sent.");
        } else if (polygons.toString().equals(GeometryUtils.NON_SPECIFIED_MULTIPOLYGON)) {
            parameters.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), polygons.toString());
        } else {
            parameters.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), objectMapper.writeValueAsString(polygons));
        }
    }
}
