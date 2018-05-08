# Stool development

To build Stool, you need:
* Java 8
* Maven 3
* [Ronn](https://github.com/rtomayko/ronn) to generate the man pages
  
Releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.
