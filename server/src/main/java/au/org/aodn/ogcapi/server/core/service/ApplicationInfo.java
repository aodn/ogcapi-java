package au.org.aodn.ogcapi.server.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public interface ApplicationInfo {
    @Slf4j
    class LogHolder {
        // Lombok injects the logger here inside the static inner class
    }

    /**
     * Load the info on first use, not at startup, so a down service cannot block startup.
     * Only a good result is kept, an empty result is retried on the next call.
     */
    class InfoCache {
        protected final Supplier<Map<String, Map<?,?>>> loader;
        protected volatile Map<String, Map<?,?>> info;

        public InfoCache(Supplier<Map<String, Map<?,?>>> loader) {
            this.loader = loader;
        }

        public Map<String, Map<?,?>> get() {
            if (info == null) {
                Map<String, Map<?,?>> result = loader.get();
                if (result == null || result.isEmpty()) {
                    return Collections.emptyMap();
                }
                info = result;
            }
            return info;
        }
    }

    /**
     * Query this is repeat code to query the info path
     * @param restTemplate - The template of the service
     * @param host - Host
     * @param path - query path
     * @return - Map of value
     */
    default Map<String, Map<?,?>> queryInfo(RestTemplate restTemplate, String host, String path) {
        if(path != null) {
            try {
                String das = String.format("%s/%s", host, path);

                LogHolder.log.info("Query service info for {}, {}", host, path);
                ResponseEntity<Map<String, Map<?, ?>>> response = restTemplate.exchange(
                        das,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {
                        }
                );

                if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                }
            } catch (Exception e) {
                // Do not throw exception that impacts service, UI will use health endpoint to decide level of
                // service, for example if geonetwork is not work, we can still provide service
                LogHolder.log.warn("Fail to query service info for {}, {}, cause: {}", host, path, e.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    /**
     * @return - The info of the service, empty map if the service cannot be reached
     */
    Map<String, Map<?,?>> getAppInfo();

    default String getName() {
        return getApplicationValue("name");
    }

    default String getVersion() {
        return getApplicationValue("version");
    }

    default String getDescription() {
        return getApplicationValue("description");
    }

    private String getApplicationValue(String key) {
        Object value = getAppInfo().getOrDefault("application", Collections.emptyMap()).getOrDefault(key, null);
        return value != null ? value.toString() : null;
    }
}
