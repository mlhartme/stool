# Stool development

To build Stool, you need:
* Java 8+
* Maven 3+
* Docker (with api 1.38+) including docker-compose. Make sure you can invoke Docker without sudo.


Build with

    mvn clean install
    
    
Releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.
