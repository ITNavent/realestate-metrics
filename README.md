# realestate-metrics
Metricas de la aplicacion sobre Springboot

![alt text](https://us.123rf.com/450wm/lenm/lenm1206/lenm120600275/14182604-ilustraci%C3%B3n-mascota-con-una-maestr%C3%ADa-de-cinta.jpg "Logo realestate-metrics")

Genera metricas de tiempos de respuesta, cantidad de llamadas y cantidad de llamadas por intervalo de tiempo por cada endpoint que cae dentro del patron definido.
También genera metricas agregadas a nivel aplicacion y cantidad de respuesta según el status http de la respuesta.

## Properties de configuracion

Abajo figuran los parametros configurables del paquete, el unico que no tiene un valor por defecto util es *metrics.endpoint.pattern* ya que es especifico de la aplicacion en cuestion, el resto salvo excepciones no haria falta sobreescribirlos.

```
metrics:
  enabled: true
  endpoint.pattern: "/v1/(ads|credits|reports).*"
  zabbix:
    serverHost: "zabbix.bumeran.biz" 
    serverPort: 10051
    listenPort: 10051
```

## Template en zabbix

Agregar en zabbix el temaplate **Springboot JMX**
