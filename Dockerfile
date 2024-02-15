FROM amazoncorretto:17
VOLUME /tmp

#COPY ./server/target/server-java-1.0.0-SNAPSHOT-exec.jar app.jar
#COPY ./server/target/server-java-5195-ci-tlc-SNAPSHOT-exec.jar app.jar
COPY ./server/target/ogcapi-java-server-*.jar app.jar
ENTRYPOINT [\
    "java",\
    "-Delasticsearch.index.name=${INDEX_NAME}",\
    "-Dapi.host=${HOST}:${PORT}",\
    "-Dserver.port=${PORT}",\
    "-Delasticsearch.serverUrl=${ELASTIC_URL}",\
    "-Delasticsearch.apiKey=${ELASTIC_KEY}",\
    "-jar",\
    "/app.jar"]