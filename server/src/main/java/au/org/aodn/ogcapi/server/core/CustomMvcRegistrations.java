package au.org.aodn.ogcapi.server.core;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * This class is used to change the behavior of the RequestMapping behavior by assuming that if
 * the method is mark with @Hidden in swagger, it means we are not going to expose it and no need
 * to do the request mapping registration.
 *
 * This is important because the OGC api contains duplicated endpoint, and we should only register one
 * of them only.
 */
@Component
public class CustomMvcRegistrations implements WebMvcRegistrations {
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new RequestMappingHandlerMapping() {

            @Override
            protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
                return method.getAnnotation(Hidden.class) == null ?
                        super.getMappingForMethod(method, handlerType) : null;

            }
        };
    }
}
