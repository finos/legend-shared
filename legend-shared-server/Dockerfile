FROM openjdk:11.0.11
COPY target/legend-shared-server-*.jar /app/bin/
CMD java -Xmx2G -Xms256M -Xss4M -cp /app/bin/*-shaded.jar -Dfile.encoding=UTF8 org.finos.legend.server.shared.staticserver.Server server /config/config.json
