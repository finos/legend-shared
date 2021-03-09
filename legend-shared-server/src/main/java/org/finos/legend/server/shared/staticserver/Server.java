// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.server.shared.staticserver;

import com.google.common.base.Strings;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Manifest;

public class Server extends Application<org.finos.legend.server.shared.staticserver.StaticServerConfiguration>
{
    public static void main(String[] args) throws Exception
    {
        if (args.length > 1 && !Files.exists(new File(args[1]).toPath()))
        {
            System.out.println("WARNING '" + args[1] + "' can't be found ");
            System.out.println("Please use -v option to map the 'config' folder to an external folder containing config.json.\n"
                    + "Example: docker run --name shared --publish 8080:8080 -v ~/work/code/legend-shared/legend-shared-server/src/main/resources/:/config -d finos/legend-shared-server:0.6.0-SNAPSHOT");

            URL resource = Server.class.getClassLoader().getResource("config.json");
            System.out.println("Using the default config sourced from :\n" + Objects.requireNonNull(resource).getPath());

            try (Scanner scanner = new Scanner(resource.openStream()))
            {
                String content = scanner.useDelimiter("\\A").next();
                System.out.println("Content:\n" + content);
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
                File file = Files.createTempFile("tempConfig", ".json", attr).toFile();
                try (FileOutputStream fos = new FileOutputStream(file))
                {
                    fos.write(content.getBytes());
                }
                System.out.println("Writing to temp file:" + file.getAbsolutePath());
                args[1] = file.getAbsolutePath();
            }
        }
        new org.finos.legend.server.shared.staticserver.Server().run(args);
    }

    private String getStaticPathFromManifest()
    {
        try
        {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements())
            {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                String path = manifest.getMainAttributes().getValue("Static-Root");
                if (!Strings.isNullOrEmpty(path))
                {
                    return path;
                }
            }
        }
        catch (IOException ignored)
        {
            // ignore
        }
        return null;
    }

    @Override
    public void initialize(Bootstrap<org.finos.legend.server.shared.staticserver.StaticServerConfiguration> bootstrap)
    {
        // Enable variable substitution with environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(true)
                )
        );
        String staticPath = getStaticPathFromManifest();
        bootstrap.addBundle(new org.finos.legend.server.shared.staticserver.StaticServerBundle<>(
                staticPath, Function.identity()));
    }

    @Override
    public void run(org.finos.legend.server.shared.staticserver.StaticServerConfiguration staticServerConfiguration,
                    Environment environment)
    {
    }
}
