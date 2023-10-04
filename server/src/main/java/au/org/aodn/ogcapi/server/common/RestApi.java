package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.common.api.ApiApi;
import au.org.aodn.ogcapi.common.api.ConformanceApi;
import au.org.aodn.ogcapi.common.api.DefaultApi;
import au.org.aodn.ogcapi.common.model.ConfClasses;
import au.org.aodn.ogcapi.common.model.LandingPage;
import au.org.aodn.ogcapi.server.core.InternalService;
import au.org.aodn.ogcapi.server.core.OGCMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

@Controller("CommonRestApi")
public class RestApi implements ApiApi, DefaultApi, ConformanceApi {

    @Autowired
    @Qualifier("CommonRestService")
    protected InternalService commonService;

    @Override
    public ResponseEntity<Void> apiGet(String f) {
        return null;
    }

    @Override
    public ResponseEntity<LandingPage> getLandingPage(String f) {
        return null;
    }

    @Override
    public ResponseEntity<ConfClasses> getConformanceDeclaration(String f) {
        List<String> result = new ArrayList<>();

        // Support the following services
        result.addAll(commonService.getConformanceDeclaration());

        switch(OGCMapper.valueOf(f.toLowerCase())) {
            case json: {
                return ResponseEntity.ok()
                        .contentType(OGCMapper.json.getMediaType())
                        .body(new ConfClasses().conformsTo(result));
            }
            default: {
                // TODO: html return needed but how?
                return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
            }
        }
    }
}
