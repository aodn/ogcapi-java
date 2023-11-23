FROM amazoncorretto:17
VOLUME /tmp

COPY ./server/target/server-java-1.0.0-SNAPSHOT-exec.jar app.jar
ENTRYPOINT ["java","-Dserver.port=${PORT}","-Delasticsearch.serverUrl=${ELASTIC_URL}","-Delasticsearch.apiKey=${ELASTIC_KEY}","-jar","/app.jar"]