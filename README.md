# realestate-metrics
Metricas y tracing de la aplicacion sobre Springboot

![alt text](https://us.123rf.com/450wm/lenm/lenm1206/lenm120600275/14182604-ilustraci%C3%B3n-mascota-con-una-maestr%C3%ADa-de-cinta.jpg "Logo realestate-metrics")

Genera metricas de tiempos de respuesta, cantidad de llamadas y cantidad de llamadas por intervalo de tiempo por cada endpoint que cae dentro del patron definido.
También genera metricas agregadas a nivel aplicacion y cantidad de respuesta según el status http de la respuesta.

## Versiones

### Version 0.1.8

Version con soporte para Springboot 2, para usar con Springboot 1 usar la version 0.0.8

Agregar en zabbix el temaplate **Springboot JMX v2**

Comenzando con esta version se necesita agregar a las properties de la aplicacion el siguiente valor.
```
management:
  metrics.export:
    jmx.enabled: true
```
Se actualiza a la ultima version de micrometer v1.1.4

Se suma endpoint de prometheus en /manage/prometheus, pero solo se expone la metrica usada para autoescalar pods: http_server_requests_uri_root_1_min_request_rate_total 


### Version 0.1.0

Primera version con soporte Springboot 2.

Ya no es necesario desactivar y de hecho no funciona si esta desactivado:
```
management:
  metrics.export:
    jmx.enabled: false
```
Por lo que se __debe__ borrar esa parte de la configuracion.

### Version 0.0.8

Agregar en zabbix el temaplate **Springboot JMX v2**

Comenzando con esta version se necesita agregar a las properties de la aplicacion el siguiente valor.
```
management:
  metrics.export:
    jmx.enabled: true
```
Se actualiza a la ultima version de micrometer v1.1.4

Se suma endpoint de prometheus en /manage/prometheus, pero solo se expone la metrica usada para autoescalar pods: http_server_requests_uri_root_1_min_request_rate_total 

### Version 0.0.7

Se suman métricas de error rate a nivel aplicacion y a nivel endpoint. Además se agrega el apdex a nivel aplicacion. https://en.wikipedia.org/wiki/Apdex 

Comenzando con esta version se necesita agregar a las properties de la aplicacion el siguiente valor.
```
management:
  metrics.export:
    jmx.enabled: false
```
Esto es necesario para poder agregar nuevos tipos de contadores, includos en la version 0.0.7

### Version 0.0.6

Se suma tracing de operacion gracias a http://opentracing.io/

Agregar en zabbix el temaplate **Springboot JMX**


## Properties de configuracion

Abajo figuran los parametros configurables del paquete, el unico que no tiene un valor por defecto util es *metrics.endpoint.pattern* ya que es especifico de la aplicacion en cuestion, el resto salvo excepciones no haria falta sobreescribirlos.

En la version 0.0.6 la opcion de trace viene desactivada por defecto ya que no contamos con un servidor comun activado por el momento y para no romper compatibilidad con la version anterior.

```
metrics:
  enabled: true
  endpoint.pattern: "/v1/(ads|credits|reports).*"
  zabbix:
    serverHost: "zabbix.bumeran.biz" 
    serverPort: 10051
    listenPort: 10051
  trace:
    enabled: false
    senderEndpoint: ""
    serviceName: ""
  apdex:
    enabled: false
    millis: numeric
```