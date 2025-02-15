FROM amazoncorretto:17
VOLUME /tmp

COPY ./server/target/ogcapi-java-server-*-exec.jar app.jar
ENTRYPOINT [\
    "java",\
    "-Delasticsearch.index.name=${INDEX_NAME}",\
    "-Delasticsearch.cloud_optimized_index.name=${CO_INDEX_NAME}",\
    "-Delasticsearch.vocabs_index.name=${VOCABS_INDEX_NAME}",\
    "-Dapi.host=${HOST}:${PORT}",\
    "-Dserver.port=${PORT}",\
    "-Delasticsearch.serverUrl=${ELASTIC_URL}",\
    "-Delasticsearch.apiKey=${ELASTIC_KEY}",\
    "-jar",\
    "/app.jar"]
