Tomcat 7.0.30
-------------

split into

  1) a home part (templates/stool/tomcat) with the following directories removed
     * conf
     * logs
     * temp
     * webapps
     * work
  2) a base part (templates/stage/tomcat) with the following directories removed:
     * bin
     * lib
     * webapps
     and conf/catalina.properties adjusted