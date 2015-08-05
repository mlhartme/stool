<xsl:stylesheet
    xmlns:x="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <x:variablelist>
      <xsl:apply-templates select=".//x:refentry"/>
    </x:variablelist>
  </xsl:template>

  <xsl:template match="x:refentry">
    <x:varlistentry><x:term><xsl:value-of select="x:refsynopsisdiv/x:cmdsynopsis/x:command"/></x:term><x:listitem><xsl:value-of select="x:refnamediv/x:refpurpose"/></x:listitem></x:varlistentry>
  </xsl:template>

  <!--
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>
-->
</xsl:stylesheet>