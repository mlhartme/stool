<xsl:stylesheet
    xmlns="http://docbook.org/ns/docbook"
    xmlns:x="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="no"/>

  <xsl:template match="/">
    <refsynopsisdiv>
      <xsl:apply-templates select=".//x:refsynopsisdiv/x:cmdsynopsis"/>
    </refsynopsisdiv>
  </xsl:template>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>