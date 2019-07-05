package com.navent.realestate.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Configuration
@ConfigurationProperties("metrics")
public class NaventMetricsProperties {
	private boolean enabled;
	private Endpoint endpoint = new Endpoint();
	private Zabbix zabbix = new Zabbix();
	private Trace trace = new Trace();
	private Apdex apdex = new Apdex();

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

	@Data
	@NoArgsConstructor
	public static class Apdex {
		private boolean enabled;
		private long millis;
	}
}
