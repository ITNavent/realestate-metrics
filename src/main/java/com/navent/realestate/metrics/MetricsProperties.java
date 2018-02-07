package com.navent.realestate.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@ConfigurationProperties("metrics")
public class MetricsProperties {
	private boolean enabled;
	private Endpoint endpoint = new Endpoint();
	private Zabbix zabbix = new Zabbix();
	private Trace trace = new Trace();

	@Data
	@NoArgsConstructor
	public static class Endpoint {
		private String pattern;
	}

	@Data
	@NoArgsConstructor
	public static class Trace {
		private boolean enabled;
		private String senderEndpoint;
		private String serviceName;
	}

	@Data
	@NoArgsConstructor
	public static class Zabbix {
		private String serverHost = "zabbix.bumeran.biz";
		private int serverPort = 10051;
		private int listenPort = 10051;
	}
}
