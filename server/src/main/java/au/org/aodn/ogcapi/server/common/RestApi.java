package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.common.api.ApiApi;
import au.org.aodn.ogcapi.common.api.ConformanceApi;
import au.org.aodn.ogcapi.common.api.DefaultApi;
import au.org.aodn.ogcapi.common.model.ConfClasses;
import au.org.aodn.ogcapi.common.model.LandingPage;
import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import au.org.aodn.ogcapi.server.core.OGCMediaTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController("CommonRestApi")
public class RestApi implements ApiApi, DefaultApi, ConformanceApi {

    @Autowired
    @Qualifier("CommonRestService")
    protected OGCApiService commonService;

    @Autowired
    @Qualifier("TileRestService")
    protected OGCApiService tileService;

    @Autowired
    @Qualifier("FeaturesRestService")
    protected OGCApiService featuresService;

    @Override
    public ResponseEntity<Void> apiGet(String f) {
        switch(OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
            case json: {
                return ResponseEntity
                        .status(HttpStatus.TEMPORARY_REDIRECT)
                        .location(URI.create("v3/api-docs"))
                        .build();
            }
            default: {
                return ResponseEntity
                        .status(HttpStatus.TEMPORARY_REDIRECT)
                        .location(URI.create("swagger-ui/index.html"))
                        .build();
            }
        }
    }

    @Override
    public ResponseEntity<LandingPage> getLandingPage(String f) {
        return null;
    }

    @Override
    public ResponseEntity<ConfClasses> getConformanceDeclaration(String f) {

        switch(OGCMediaTypeMapper.valueOf(f.toLowerCase())) {
            case json: {
                List<String> result = new ArrayList<>();

                // Support the following services
                result.addAll(commonService.getConformanceDeclaration());
                result.addAll(tileService.getConformanceDeclaration());
                result.addAll(featuresService.getConformanceDeclaration());

                return ResponseEntity.ok()
                        .contentType(OGCMediaTypeMapper.json.getMediaType())
                        .body(new ConfClasses().conformsTo(result));
            }
            default: {
                /**
                 * https://opengeospatial.github.io/ogcna-auto-review/19-072.html
                 *
                 * The OGC API — Common Standard does not mandate a specific encoding or format for
                 * representations of resources. However, both HTML and JSON are commonly used encodings for spatial
                 * data on the web. The HTML and JSON requirements classes specify the encoding of resource
                 * representations using:
                 *
                 *     HTML
                 *     JSON
                 *
                 * Neither of these encodings is mandatory. An implementer of the API-Common Standard may decide
                 * to implement other encodings instead of, or in addition to, these two.
                 */
                // TODO: html return
                return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
            }
        }
    }
}
