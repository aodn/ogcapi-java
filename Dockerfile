FROM amazoncorretto:17
VOLUME /tmp

COPY ./server/target/ogcapi-java-server-*-exec.jar app.jar
ENTRYPOINT [\
    "java",\
    "-Delasticsearch.index.name=${INDEX_NAME}",\
    "-Delasticsearch.search_as_you_type.category_suggest.index_name=${CATEGORY_INDEX_NAME}",\
    "-Dapi.host=${HOST}:${PORT}",\
    "-Dserver.port=${PORT}",\
    "-Delasticsearch.serverUrl=${ELASTIC_URL}",\
    "-Delasticsearch.apiKey=${ELASTIC_KEY}",\
    "-jar",\
    "/app.jar"]
