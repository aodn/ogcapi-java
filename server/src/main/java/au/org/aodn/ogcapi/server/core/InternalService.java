package au.org.aodn.ogcapi.server.core;

import java.util.List;

/**
 *
 */
public interface InternalService {
    /**
     * You can find conformance id here https://docs.ogc.org/is/19-072/19-072.html#ats_core
     * @return
     */
    List<String> getConformanceDeclaration();
}
