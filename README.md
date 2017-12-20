# realestate-metrics
Metricas de la aplicacion sobre Springboot

Genera metricas de tiempos de respuesta, cantidad de llamadas y cantidad de llamadas por intervalo de tiempo por cada endpoint que cae dentro del patron definido.
También genera metricas agregadas a nivel aplicacion y cantidad de respuesta según el status http de la respuesta.

## Properties de configuracion

metrics:
  enabled: true
  endpoint.pattern: "" ej: "/v1/(ads|credits|reports).*"
  zabbix:
    serverHost: "zabbix.bumeran.biz" 
	serverPort: 10051
	listenPort: 10051
	
## Template en zabbix

Spring JMX