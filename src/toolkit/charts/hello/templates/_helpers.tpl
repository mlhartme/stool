{{/*
Name
*/}}
{{- define "name" -}}
{{- .Release.Name }}
{{- end }}

{{/*
Name without dot -- i.e. a dns label
*/}}
{{- define "dnsLabel" -}}
{{ .Release.Name | replace "." "--" }}
{{- end }}
