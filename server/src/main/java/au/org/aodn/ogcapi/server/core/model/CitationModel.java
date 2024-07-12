package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("unused")
public class CitationModel {

    protected String suggestedCitation;
    protected List<String> useLimitations;
    protected List<String> otherConstraints;

    protected static Logger logger = LoggerFactory.getLogger(CitationModel.class);

    public static CitationModel constructByJsonString(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonString, CitationModel.class);
        } catch (IOException e) {
            logger.error("Failed to parse CitationModel from json string: {}", jsonString, e);
        }
        return null;
    }
}
