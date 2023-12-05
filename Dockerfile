FROM amazoncorretto:17

RUN apt-get update \
    && apt-get install -y jq \
    && apt-get clean && apt-get autoclean && apt-get autoremove \
    && rm -rf /var/lib/apt/lists/*

VOLUME /tmp
COPY ./server/target/server-java-1.0.0-SNAPSHOT-exec.jar app.jar

ENTRYPOINT ["java","-Dapi.host=${HOST}:${PORT}","-Dserver.port=${PORT}","-Delasticsearch.serverUrl=${ELASTIC_URL}","-Delasticsearch.apiKey=${ELASTIC_KEY}","-jar","/app.jar"]