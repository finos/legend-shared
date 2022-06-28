[![FINOS - Incubating](https://cdn.jsdelivr.net/gh/finos/contrib-toolbox@master/images/badge-incubating.svg)](https://finosfoundation.atlassian.net/wiki/display/FINOS/Incubating)
[![Maven Central](https://img.shields.io/maven-central/v/org.finos.legend.shared/legend-shared-server.svg?maxAge=2592000)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22legend-shared)
![Build CI](https://github.com/finos/legend-shared/workflows/Build%20CI/badge.svg)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=legend-shared&metric=security_rating&token=4c3f5479f7a32d754c947207987569fc14c7bba9)](https://sonarcloud.io/dashboard?id=legend-shared)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=legend-shared&metric=bugs&token=4c3f5479f7a32d754c947207987569fc14c7bba9)](https://sonarcloud.io/dashboard?id=legend-shared)

# legend-shared

Shared code used across Legend applications for universal server-side functionality - hosting static files, performing authentication etc.

The important tenets for this project are:
* Code must be genuinely used by the majority of Legend applications
* New dependencies must be carefully scrutinized - the goal is to minimize unnecessary dependencies in apps.

## Development setup

This application uses Maven 3.6+ and JDK 8. Simply run `mvn install` to compile.

## Roadmap

Visit our [roadmap](https://github.com/finos/legend#roadmap) to know more about the upcoming features.

## Contributing

Visit Legend [Contribution Guide](https://github.com/finos/legend/blob/master/CONTRIBUTING.md) to learn how to contribute to Legend.


## License

Copyright 2020 Goldman Sachs

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
