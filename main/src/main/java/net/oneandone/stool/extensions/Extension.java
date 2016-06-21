/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

public interface Extension {
    /**
     * @return vhost names mapped to a docroot. Docroot may be null which indicates that this vhost must not be added
     * to the tomcat configration (this is useful to only allocate ports)
     */
    Map<String, FileNode> vhosts(Stage stage) throws IOException;

    void beforeStart(Stage stage) throws IOException;

    void beforeStop(Stage stage) throws IOException;
/*
INFO] +   33 kb net.oneandone.stool:site:3.4.0-SNAPSHOT
[INFO] +  289 kb net.oneandone:sushi:3.1.0
[INFO] +   51 kb net.oneandone:inline:1.0.2-20160617.125942-1
[INFO] +   15 kb net.oneandone.maven:embedded:3.12.1
[INFO] +   51 kb org.codehaus.plexus:plexus-classworlds:2.5.2
[INFO] +  623 kb org.apache.maven:maven-core:3.3.9
[INFO] +  160 kb org.apache.maven:maven-model:3.3.9
[INFO] +   42 kb org.apache.maven:maven-settings-builder:3.3.9
[INFO] +   15 kb org.apache.maven:maven-builder-support:3.3.9
[INFO] +   27 kb org.apache.maven:maven-repository-metadata:3.3.9
[INFO] +   46 kb org.apache.maven:maven-plugin-api:3.3.9
[INFO] +  173 kb org.apache.maven:maven-model-builder:3.3.9
[INFO] + 2254 kb com.google.guava:guava:19.0
[INFO] +  169 kb org.eclipse.aether:aether-impl:1.0.2.v20150114
[INFO] +  201 kb org.eclipse.sisu:org.eclipse.sisu.plexus:0.3.2
[INFO] +   44 kb javax.enterprise:cdi-api:1.0
[INFO] +    6 kb javax.annotation:jsr250-api:1.0
[INFO] +  369 kb org.eclipse.sisu:org.eclipse.sisu.inject:0.3.2
[INFO] +  414 kb com.google.inject:guice:4.0
[INFO] +    2 kb javax.inject:javax.inject:1
[INFO] +    4 kb aopalliance:aopalliance:1.0
[INFO] +   61 kb org.codehaus.plexus:plexus-interpolation:1.21
[INFO] +  239 kb org.codehaus.plexus:plexus-utils:3.0.22
[INFO] +    4 kb org.codehaus.plexus:plexus-component-annotations:1.6
[INFO] +   28 kb org.sonatype.plexus:plexus-sec-dispatcher:1.3
[INFO] +   13 kb org.sonatype.plexus:plexus-cipher:1.4
[INFO] +  424 kb org.apache.commons:commons-lang3:3.4
[INFO] +   54 kb org.apache.maven:maven-artifact:3.3.9
[INFO] +   43 kb org.apache.maven:maven-settings:3.3.9
[INFO] +  284 kb org.apache.maven:maven-compat:3.3.9
[INFO] +   65 kb org.apache.maven:maven-aether-provider:3.3.9
[INFO] +   30 kb org.eclipse.aether:aether-spi:1.0.2.v20150114
[INFO] +  133 kb org.eclipse.aether:aether-api:1.0.2.v20150114
[INFO] +  143 kb org.eclipse.aether:aether-util:1.0.2.v20150114
[INFO] +   36 kb org.eclipse.aether:aether-connector-basic:1.0.2.v20150114
[INFO] +   25 kb org.eclipse.aether:aether-transport-wagon:1.0.2.v20150114
[INFO] +    9 kb org.eclipse.aether:aether-transport-file:1.0.2.v20150114
[INFO] +   35 kb org.eclipse.aether:aether-transport-http:1.0.2.v20150114
[INFO] +  716 kb org.apache.httpcomponents:httpclient:4.5.1
[INFO] +  258 kb commons-codec:commons-codec:1.9
[INFO] +   52 kb org.apache.maven.wagon:wagon-provider-api:2.10
[INFO] +   11 kb org.apache.maven.wagon:wagon-file:2.10
[INFO] +  278 kb commons-lang:commons-lang:2.6
[INFO] +   33 kb org.apache.maven.wagon:wagon-http:2.10
[INFO] +   11 kb org.apache.maven.wagon:wagon-http-shared:2.10
[INFO] +  287 kb org.jsoup:jsoup:1.7.2
[INFO] +  170 kb commons-io:commons-io:2.2
[INFO] +  319 kb org.apache.httpcomponents:httpcore:4.4.4
[INFO] +  507 kb javax.mail:mail:1.5.0-b01
[INFO] +   40 kb org.slf4j:slf4j-api:1.7.21
[INFO] +  460 kb ch.qos.logback:logback-core:1.1.7
[INFO] +  297 kb ch.qos.logback:logback-classic:1.1.7
[INFO] +  227 kb com.google.code.gson:gson:2.7
[INFO] +  259 kb net.oneandone.stool:main:3.4.0-SNAPSHOT

 */
    void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result);
}
