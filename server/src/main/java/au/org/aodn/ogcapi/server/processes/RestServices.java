package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.exception.SseClientGoneException;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.SseEventName;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.DownloadWfsDataService;
import au.org.aodn.ogcapi.server.core.service.sse.SseStreamHandler;
import au.org.aodn.ogcapi.server.core.util.EmailUtils;
import au.org.aodn.ogcapi.server.core.util.SubsetParametersUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.io.InputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RestServices {

    private final BatchClient batchClient;
    private final ObjectMapper objectMapper;
    private final String batchJobDefinition;
    private final String batchJobQueue;

    // How often the cloud-optimised estimate reassures its client while data-access-service
    // works. It has to stay well under the shortest idle timeout in front of this service -
    // CloudFront allows 30s between packets - so a DAS that fails without ever answering cannot
    // leave the connection quiet long enough to be dropped before the failure is reported.
    // Nothing else emits keep-alive on this stream, so this is the rate the client sees, and it
    // does not matter that it happens to match the DAS heartbeat. Lowered in tests to keep them
    // fast.
    private long estimateKeepAliveSeconds = 5;

    @Autowired
    private DownloadWfsDataService downloadWfsDataService;

    @Autowired
    private DasService dasService;

    public RestServices(BatchClient batchClient, ObjectMapper objectMapper, String batchJobDefinition, String batchJobQueue) {
        this.batchClient = batchClient;
        this.objectMapper = objectMapper;
        this.batchJobDefinition = batchJobDefinition;
        this.batchJobQueue = batchJobQueue;
    }

    public void notifyUser(String recipient, String uuid, String key, String startDate, String endDate, Object multiPolygon, String collectionTitle,
                           String fullMetadataLink,
                           String suggestedCitation,
                           String outputFormat) {

        String aodnInfoSender = "no.reply@aodn.org.au";

        try (SesClient ses = SesClient.builder().build()) {
            var subject = Content.builder().data("Start processing data file whose uuid is: " + uuid).build();
            var content = Content.builder().data(generateStartedEmailContent(uuid, key, startDate, endDate, multiPolygon, collectionTitle, fullMetadataLink, suggestedCitation, outputFormat)).build();
            var destination = Destination.builder().toAddresses(recipient).build();

            var body = Body.builder().html(content).build();
            var message = Message.builder()
                    .subject(subject)
                    .body(body)
                    .build();

            SendEmailRequest request = SendEmailRequest.builder()
                    .message(message)
                    .source(aodnInfoSender)
                    .destination(destination)
                    .build();

            ses.sendEmail(request);

        } catch (Exception e) {
            // Best effort: this runs after the batch job was accepted, so a failure to notify
            // must not surface as a failed download request for a job that is already running.
            log.error("Error sending email: {}", e.getMessage());
        }
    }

    /**
     * Build the AWS Batch parameters for a download: the shared subset filters (uuid, key,
     * dates, multi_polygon, output format) exactly as the estimate builds them, plus the
     * download-only fields.
     *
     * <p>Separate from the submit so a request that has to wait for a free slot is validated
     * and rendered at accept time, and releasing it later is a plain submit.
     */
    public Map<String, String> buildDownloadParameters(DownloadRequest request) throws JsonProcessingException {
        Map<String, String> parameters = SubsetParametersUtils.buildSubsetParameters(
                objectMapper, request.uuid(), request.key(), request.startDate(), request.endDate(),
                request.multiPolygon(), request.outputFormat());
        parameters.put(DatasetDownloadEnums.Parameter.RECIPIENT.getValue(), request.recipient());
        parameters.put(DatasetDownloadEnums.Parameter.COLLECTION_TITLE.getValue(), request.collectionTitle());
        parameters.put(DatasetDownloadEnums.Parameter.FULL_METADATA_LINK.getValue(), request.fullMetadataLink());
        parameters.put(DatasetDownloadEnums.Parameter.SUGGESTED_CITATION.getValue(), request.suggestedCitation());
        parameters.put(
                DatasetDownloadEnums.Parameter.TYPE.getValue(),
                DatasetDownloadEnums.Type.SUB_SETTING.getValue()
        );
        return parameters;
    }

    /**
     * The AWS Batch job name for a download. Note this sanitises the address, so it is not a
     * safe key for the owning user - read the recipient job parameter instead.
     */
    public static String downloadJobName(String recipient) {
        return "generating-data-file-for-" + recipient.replaceAll("[^a-zA-Z0-9-_]", "-");
    }

    /** Submit a prepared download to the configured queue and job definition. */
    public String submitDownloadJob(String jobName, Map<String, String> parameters) {
        String jobId = submitJob(jobName, this.batchJobQueue, this.batchJobDefinition, parameters);
        log.info("Job submitted with ID: {}", jobId);
        return jobId;
    }

    private String submitJob(String jobName, String jobQueue, String jobDefinition, Map<String, String> parameters) {

        // Filter out null or empty parameter values before submitting to AWS Batch.
        // AWS Batch returns "Parameter values must be provided" when the job definition
        // declares parameters but some submitted values are null/empty.
        //
        // A defensive copy, never the caller's own map: a held download keeps this exact map
        // instance around so the status endpoint can describe it while it waits, and readers
        // of that map run on other threads with no synchronization of their own.
        Map<String, String> submitParameters = parameters;
        if (parameters != null) {
            var suggestedCitation = parameters.get(DatasetDownloadEnums.Parameter.SUGGESTED_CITATION.getValue());
            // empty suggested citation is acceptable since it may be from external orgs
            if (suggestedCitation == null || suggestedCitation.isEmpty()) {
                log.warn("Suggested citation is null or empty for job '{}'. Submitting with unavailable as value.", jobName);
                submitParameters = new HashMap<>(parameters);
                submitParameters.put(DatasetDownloadEnums.Parameter.SUGGESTED_CITATION.getValue(), "unavailable");
            }
        }

        SubmitJobRequest submitJobRequest = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueue)
                .jobDefinition(jobDefinition)
                .parameters(submitParameters)
                .build();

        SubmitJobResponse submitJobResponse = batchClient.submitJob(submitJobRequest);
        String jobId = submitJobResponse.jobId();

        // Callers treat a returned job id as proof the job was accepted (the user gets a
        // "processing started" email off the back of it), so never hand back a blank one.
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalStateException("AWS Batch did not return a job id for job '" + jobName + "'");
        }
        return jobId;
    }

    // package-private so JobStartedEmailPreview (test scope) can render it
    String generateStartedEmailContent(
            String uuid,
            String key,
            String startDate,
            String endDate,
            Object multipolygon,
            String collectionTitle,
            String fullMetadataLink,
            String suggestedCitation,
            String outputFormat
    ) {
        try (InputStream inputStream = getClass().getResourceAsStream("/job-started-email.html")) {

            if (inputStream == null) {
                log.error("Email template not found");
                throw new RuntimeException("Email template not found");
            }

            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // Generate subsetting section (returns empty string if no subsetting)
            String subsettingSection = EmailUtils.generateSubsettingSection(
                    startDate,
                    endDate,
                    multipolygon,
                    objectMapper
            );

            // Keys arrive comma-separated; show one per line. Absent means "all files".
            String keysDisplay = (key != null && !key.isBlank()) ? key.replace(",", "<br>") : "*";

            // Replace all variables in one chain
            return template
                    .replace("{{HEADER_IMG}}", EmailUtils.readBase64Image("header.txt"))
                    .replace("{{collectionTitle}}", collectionTitle != null ? collectionTitle : "")
                    .replace("{{uuid}}", uuid != null ? uuid : "")
                    .replace("{{keys}}", keysDisplay)
                    .replace("{{outputFormat}}", outputFormat != null ? outputFormat.toUpperCase() : "")
                    .replace("{{subsettingSection}}", subsettingSection)
                    .replace("{{BBOX_IMG}}", EmailUtils.readBase64Image("bbox.txt"))
                    .replace("{{POLYGON_IMG}}", EmailUtils.readBase64Image("polygon.txt"))
                    .replace("{{TIME_RANGE_IMG}}", EmailUtils.readBase64Image("time-range.txt"))
                    .replace("{{ATTRIBUTES_IMG}}", EmailUtils.readBase64Image("attributes.txt"))
                    .replace("{{fullMetadataLink}}", fullMetadataLink != null ? fullMetadataLink : "")
                    .replace("{{suggestedCitation}}", suggestedCitation != null ? suggestedCitation : "")
                    .replace("{{INSTAGRAM_IMG}}", EmailUtils.readBase64Image("instagram.txt"))
                    .replace("{{FACEBOOK_IMG}}", EmailUtils.readBase64Image("facebook.txt"))
                    .replace("{{BLUESKY_IMG}}", EmailUtils.readBase64Image("bluesky.txt"))
                    .replace("{{LINKEDIN_IMG}}", EmailUtils.readBase64Image("linkedin.txt"));

        } catch (IOException e) {
            log.error("Failed to load email template", e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }

    public SseEmitter downloadWfsDataWithSse(String uuid,
                                             String startDate,
                                             String endDate,
                                             Object multiPolygon,
                                             List<String> fields,
                                             String layerName,
                                             String outputFormat) {

        return SseStreamHandler.stream(uuid, session -> {
            validateWfsSseInputs(uuid, layerName, outputFormat);

            SseEmitter emitter = session.getEmitter();

            // STEP 1: Send connection established event
            session.send(SseEventName.CONNECTION_ESTABLISHED, Map.of(
                    "message", "Starting WFS download for UUID: " + uuid,
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 2: Start keep-alive mechanism for WFS server wait time. The
            // payload reflects whether the WFS server has started responding.
            AtomicBoolean wfsServerResponded = new AtomicBoolean(false);
            session.startKeepAlive(20, () -> Map.of(
                    "message", wfsServerResponded.get() ?
                            "WFS data streaming in progress..." : "Waiting for WFS server response...",
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 3: Do preparation work: Collection lookup from Elasticsearch, WFS validation, Field retrieval, URL building
            String wfsRequestUrl = downloadWfsDataService.prepareWfsRequestUrl(
                    uuid, startDate, endDate, multiPolygon, fields, layerName, outputFormat, -1L, false
            );

            // STEP 4: Make the WFS call: Streaming the response directly to client via SSE
            downloadWfsDataService.streamWfsDataWithSse(
                    wfsRequestUrl,
                    uuid,
                    layerName,
                    outputFormat,
                    emitter,
                    wfsServerResponded
            );
        });
    }

    /**
     * Estimate the download size of a WFS (GeoServer) subset over SSE.
     */
    public SseEmitter estimateWfsDownloadWithSse(String uuid,
                                                 String startDate,
                                                 String endDate,
                                                 Object multiPolygon,
                                                 List<String> fields,
                                                 String layerName,
                                                 String outputFormat) {

        return SseStreamHandler.stream(uuid, session -> {
            validateWfsSseInputs(uuid, layerName, outputFormat);

            // STEP 1: Send connection established event
            session.send(SseEventName.CONNECTION_ESTABLISHED, Map.of(
                    "message", "Starting WFS download size estimate for UUID: " + uuid,
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 2: Start keep-alive mechanism while waiting for the WFS server
            session.startKeepAlive(20, () -> Map.of(
                    "message", "Waiting for WFS server response...",
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 3: Compute the size estimate and forward it as a single event
            try {
                BigInteger est = downloadWfsDataService.estimateDownloadSize(
                        uuid,
                        layerName,
                        startDate,
                        endDate,
                        multiPolygon,
                        fields,
                        outputFormat
                );
                session.send(est != null ? SseEventName.ESTIMATE_COMPLETE : SseEventName.ESTIMATE_FAILED, Map.of(
                        "size", est != null ? est : "",
                        "timestamp", System.currentTimeMillis()
                ));
            } catch (Exception e) {
                log.error("WFS size estimation failed for UUID {} layer {}", uuid, layerName, e);
                session.send(SseEventName.ESTIMATE_FAILED, Map.of(
                        "message", "Size estimation failed: " + e.getMessage(),
                        "timestamp", System.currentTimeMillis()
                ));
            } finally {
                session.complete();
            }
        });
    }

    /**
     * Estimate the download size of a cloud-optimised (zarr/parquet) subset over SSE.
     */
    public SseEmitter estimateCloudOptimisedDownloadWithSse(String uuid,
                                                            String key,
                                                            String startDate,
                                                            String endDate,
                                                            Object multiPolygon,
                                                            String outputFormat) {

        return SseStreamHandler.stream(uuid, session -> {
            // Validate parameters
            if (uuid == null || outputFormat == null) {
                throw new IllegalArgumentException("Missing uuid or output format");
            }

            // Parse the request exactly as the download batch job does, so the
            // estimate reflects what the same subset would actually produce.
            Map<String, String> parameters = SubsetParametersUtils.buildSubsetParameters(
                    objectMapper, uuid, key, startDate, endDate, multiPolygon, outputFormat);

            // STEP 1: Send connection established event
            session.send(SseEventName.CONNECTION_ESTABLISHED, Map.of(
                    "message", "Starting cloud-optimised size estimate for UUID: " + uuid,
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 2: Keep the connection alive while DAS works, two ways.
            // 1. This ticker, one keep-alive per interval, so the client keeps receiving bytes.
            // 2. probeClient on each DAS heartbeat below, so a client that has gone stops the
            // DAS call instead of leaving it running.
            session.startKeepAlive(estimateKeepAliveSeconds, () -> Map.of(
                    "message", "Estimating download size...",
                    "timestamp", System.currentTimeMillis()
            ));

            // STEP 3: Call the data-access-service estimate endpoint and forward the result
            try {
                String estimateJson = dasService.estimateCloudOptimisedDownloadSize(
                        uuid, parameters, session::probeClient);
                session.send(SseEventName.ESTIMATE_COMPLETE, estimateJson);
            } catch (Exception e) {
                SseClientGoneException clientGone = SseClientGoneException.find(e);
                if (clientGone != null) {
                    // Not an estimate failure: the client left, and that is what aborted the
                    // call. Rethrow so the shared handler logs it as a disconnect; there is no
                    // socket left to report anything on.
                    throw clientGone;
                }
                // ERROR level, like the shared handler's upstream branch, so New Relic can alert
                // on a failing data-access-service instead of it only showing up as a silent
                // estimate in the portal.
                log.error("Cloud-optimised size estimation failed for UUID {}", uuid, e);
                session.send(SseEventName.ESTIMATE_FAILED, Map.of(
                        "message", "Size estimation failed: " + e.getMessage(),
                        "timestamp", System.currentTimeMillis()
                ));
            } finally {
                session.complete();
            }
        });
    }

    /**
     * Shared input validation for the two SSE flows.
     */
    private void validateWfsSseInputs(String uuid, String layerName, String outputFormat) {
        if (uuid == null || layerName == null || layerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Layer name and Uuid are required");
        }
        if (FeatureRequest.GeoServerOutputFormat.fromString(outputFormat) == FeatureRequest.GeoServerOutputFormat.UNKNOWN) {
            throw new IllegalArgumentException(String.format("Missing output format [%s]", outputFormat));
        }
    }
}
