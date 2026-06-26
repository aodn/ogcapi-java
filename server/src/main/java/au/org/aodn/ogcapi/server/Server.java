package au.org.aodn.ogcapi.server;

import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(DASConfig.class)
public class Server {

    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
