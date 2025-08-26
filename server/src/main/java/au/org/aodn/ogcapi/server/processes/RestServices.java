package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadWfsDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RestServices {

    private final BatchClient batchClient;
    private final ObjectMapper objectMapper;

    @Autowired
    private DownloadWfsDataService downloadWfsDataService;

    public RestServices(BatchClient batchClient, ObjectMapper objectMapper) {
        this.batchClient = batchClient;
        this.objectMapper = objectMapper;
    }

    public void notifyUser(String recipient, String uuid, String startDate, String endDate) {

        String aodnInfoSender = "no.reply@aodn.org.au";

        try (SesClient ses = SesClient.builder().build()) {
            var subject = Content.builder().data("Start processing data file whose uuid is: " + uuid).build();
            var content = Content.builder().data(generateStartedEmailContent(startDate, endDate)).build();
            var destination = Destination.builder().toAddresses(recipient).build();

            var body = Body.builder().text(content).build();
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

        } catch (SesException e) {
            log.error("Error sending email: {}", e.getMessage());
        }
    }

    public ResponseEntity<String> downloadData(
            String id,
            String startDate,
            String endDate,
            Object polygons,
            String recipient
    ) throws JsonProcessingException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put(DatasetDownloadEnums.Parameter.UUID.getValue(), id);
        parameters.put(DatasetDownloadEnums.Parameter.START_DATE.getValue(), startDate);
        parameters.put(DatasetDownloadEnums.Parameter.END_DATE.getValue(), endDate);
        parameters.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), objectMapper.writeValueAsString(polygons));
        parameters.put(DatasetDownloadEnums.Parameter.RECIPIENT.getValue(), recipient);

        parameters.put(
                DatasetDownloadEnums.Parameter.TYPE.getValue(),
                DatasetDownloadEnums.Type.SUB_SETTING.getValue()
        );

        String jobId = submitJob(
                "generating-data-file-for-" + recipient.replaceAll("[^a-zA-Z0-9-_]", "-"),
                DatasetDownloadEnums.JobQueue.GENERATING_CSV_DATA_FILE.getValue(),
                DatasetDownloadEnums.JobDefinition.GENERATE_CSV_DATA_FILE.getValue(),
                parameters);
        log.info("Job submitted with ID: " + jobId);
        return ResponseEntity.ok("Job submitted with ID: " + jobId);
    }

    private String submitJob(String jobName, String jobQueue, String jobDefinition, Map<String, String> parameters) {

        SubmitJobRequest submitJobRequest = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueue)
                .jobDefinition(jobDefinition)
                .parameters(parameters)
                .build();

        SubmitJobResponse submitJobResponse = batchClient.submitJob(submitJobRequest);
        return submitJobResponse.jobId();
    }

    public ResponseEntity<StreamingResponseBody> downloadWfsData(
            String uuid,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> fields,
            String layerName) {

        return downloadWfsDataService.downloadWfsData(uuid, startDate, endDate, multiPolygon, fields, layerName);
    }

    private String generateStartedEmailContent(String startDate, String endDate) {
        return "Your request has been received. Date range: Start Date: " +
                startDate + ", End Date: " + endDate + ". Please wait for the result. " +
                "After the process is completed, you will receive an email " +
                "with the download link.";
    }
}
