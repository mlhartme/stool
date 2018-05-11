# Stool development

To build Stool, you need:
* Java 8
* Maven 3
* [Ronn](https://github.com/rtomayko/ronn) to generate the man pages; to make gem setup work on HighSierra, i had to 
  update ruby 2.3.3 to 2.5 with `brew install ruby`, it also update gem 2.5.3 to 2.7.7). CAUTION: restart your shell
  to see the new gem.

Releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.
