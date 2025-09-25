package au.org.aodn.ogcapi.server.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// This is only for safely swapping the health check. Will be removed when swapping is done.
@RestController
@RequestMapping(value="")
public class TempApi {

    @GetMapping("/manage/health")
    public String health() {
        return "Healthy from TempApi";
    }
}
