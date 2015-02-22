Maven Site: http://mlhartme.github.io/stool/

Building

Add to your setting

    <profile>
     <id>stool</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
     <repositories>
       <repository>
         <id>spring-milestone</id>
         <url>http://repo.spring.io/milestone/</url>
       </repository>
     </repositories>
   </profile>
