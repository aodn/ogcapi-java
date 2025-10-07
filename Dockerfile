FROM amazoncorretto:17
VOLUME /tmp

ENV MAX_HEAP_PERCENTAGE=70

COPY ./server/target/ogcapi-java-server-*-exec.jar app.jar
ENTRYPOINT [\
    "java",\
    "-XX:MaxRAMPercentage=${MAX_HEAP_PERCENTAGE}",\
    "-Delasticsearch.index.name=${INDEX_NAME}",\
    "-Delasticsearch.cloud_optimized_index.name=${CO_INDEX_NAME}",\
    "-Delasticsearch.vocabs_index.name=${VOCABS_INDEX_NAME}",\
    "-Dapi.host=${HOST}:${PORT}",\
    "-Dserver.port=${PORT}",\
    "-Delasticsearch.serverUrl=${ELASTIC_URL}",\
    "-Delasticsearch.apiKey=${ELASTIC_KEY}",\
    "-Ddata-access-service.host=${DAS_HOST}",\
    "-Ddata-access-service.secret=${DAS_SECRET}",\
    "--enable-preview",\
    "-jar",\
    "/app.jar"]
