package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.processes.model.InlineOrRefData;

public record InlineValue(String message) implements InlineOrRefData {}
