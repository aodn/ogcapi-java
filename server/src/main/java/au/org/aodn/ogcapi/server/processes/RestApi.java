package au.org.aodn.ogcapi.server.processes;


import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("ProcessesRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi  {

    @PostMapping(path="/processes/download/{collectionId}")
    public ResponseEntity downloadData(
            @PathVariable("collectionId") String collectionId,
            @Parameter(in = ParameterIn.QUERY, description = "start date") @Valid String startDate,
            @Parameter(in = ParameterIn.QUERY, description = "end date") @Valid String endDate,
            @Parameter(in = ParameterIn.QUERY, description = "bounding box") @Valid String bbox
    ){
        // test return
        return ResponseEntity.ok().build();
    }

}
