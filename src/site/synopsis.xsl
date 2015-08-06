<xsl:stylesheet
    xmlns:x="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <x:itemizedlist>
      <xsl:apply-templates select=".//x:refentry"/>
    </x:itemizedlist>
  </xsl:template>

  <xsl:template match="x:refentry">
    <x:listitem><x:para><x:command><xsl:value-of select="x:refsynopsisdiv/x:cmdsynopsis/x:command"/></x:command> - <xsl:value-of select="x:refnamediv/x:refpurpose"/></x:para></x:listitem>
  </xsl:template>

</xsl:stylesheet>