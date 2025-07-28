package au.org.aodn.ogcapi.server.features.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DownloadableField {
    @JsonProperty("label")
    private String label;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("name")
    private String name;
} 
