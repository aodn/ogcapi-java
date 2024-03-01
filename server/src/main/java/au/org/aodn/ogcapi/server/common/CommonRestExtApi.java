package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.service.Search;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("CommonRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext")
public class CommonRestExtApi {

    @Autowired
    protected Search searchService;
    @GetMapping(path="/autocomplete")
    public ResponseEntity<List<String>> getAutocompleteSuggestions(@RequestParam String input) throws java.lang.Exception {
        return searchService.getAutocompleteSuggestions(input);
    }
}
