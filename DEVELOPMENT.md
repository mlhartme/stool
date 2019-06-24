# Stool development

To build Stool, you need:
* Java 8+
* Maven 3+
* Docker with api 1.38+
* [Ronn](https://github.com/rtomayko/ronn) to generate the man pages

Build with

    mvn clean install
    
    
Releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.
