FROM openjdk:11.0.15-bullseye
COPY target/legend-shared-server-*.jar /app/bin/
CMD java -cp /app/bin/*-shaded.jar \
-Xmx2G \
-Xms256M \
-Xss4M \
-Dfile.encoding=UTF8 \
org.finos.legend.server.shared.staticserver.Server server /config/config.json
