package org.finos.legend.server.shared;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.finos.legend.server.shared.staticserver.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Collectors;

/*
    Helper class to launch a server with some generated configuration.
    The main run method launches the server and exits after a few minutes.
 */
public class ServerIntegrationTestUtil
{
    private MongoServer mongoServer;
    private int mongoServerPort;
    private ServerSocket serverPort;
    private Path workingDir;

    public static void main(String[] args) throws Exception
    {
        new ServerIntegrationTestUtil().run();
    }

    public void run() throws Exception
    {
        this.startMongo();
        this.generateServerJson();
        this.runServer();
    }

    private void startMongo()
    {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoServerPort = mongoServer.bind().getPort();
    }

    private void generateServerJson() throws Exception
    {
        serverPort = new ServerSocket(0);
        serverPort.close();
        this.workingDir = this.createConfigJson();
    }

    public void runServer() throws Exception
    {
        Path configFilePath = workingDir.resolve("config.json");

        Server.main(new String[]{"server", configFilePath.toAbsolutePath().toString()});
        String serverUri = String.format("http://127.0.0.1:%d", serverPort.getLocalPort());
        System.out.println(serverUri);
        long sleepDuration = Duration.ofMinutes(10).toMillis();
        System.out.println("Sleeping for " + sleepDuration + " millis");
        Thread.sleep(sleepDuration);
    }

    private Path createConfigJson() throws URISyntaxException, IOException
    {
        URI configUri = ServerIntegrationTestUtil.class.getResource("/server/config.json").toURI();
        String configJson = Files.readAllLines(Paths.get(configUri)).stream().collect(Collectors.joining("\n"));

        Path tempDirectory = Files.createTempDirectory("server-integration");

        configJson = configJson.replaceAll("__MONGO_URI__",  String.format("mongodb://127.0.01:%d", this.mongoServerPort));
        configJson = configJson.replaceAll("__SERVER_PORT__", String.valueOf(this.serverPort.getLocalPort()));
        Path configFilePath = Files.write(tempDirectory.resolve("config.json"), configJson.getBytes(StandardCharsets.UTF_8));
        return tempDirectory;
    }
}
