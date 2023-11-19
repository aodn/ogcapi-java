package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class TemporalModel {
    protected ZonedDateTime start;
    protected ZonedDateTime end;
}
