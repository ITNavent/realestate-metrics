# realestate-metrics
Metricas y tracing de la aplicacion sobre Springboot

![alt text](https://us.123rf.com/450wm/lenm/lenm1206/lenm120600275/14182604-ilustraci%C3%B3n-mascota-con-una-maestr%C3%ADa-de-cinta.jpg "Logo realestate-metrics")

Genera metricas de tiempos de respuesta, cantidad de llamadas y cantidad de llamadas por intervalo de tiempo por cada endpoint que cae dentro del patron definido.
También genera metricas agregadas a nivel aplicacion y cantidad de respuesta según el status http de la respuesta.

## Versiones

A partir de la version 0.0.6 se suma tracing de operacion gracias a http://opentracing.io/

A partir de la version 0.0.7 se suman métricas de error rate a nivel aplicacion y a nivel endpoint. Además se agrega el apdex a nivel aplicacion. https://en.wikipedia.org/wiki/Apdex 

Comenzando con esta version se necesita agregar a las properties de la aplicacion el siguiente valor.
```
management:
  metrics.export:
    jmx.enabled: false
```
Esto es necesario para poder agregar nuevos tipos de contadores, includos en la version 0.0.7


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

## Template en zabbix

Agregar en zabbix el temaplate **Springboot JMX**
