package com.navent.realestate.metrics;

import java.net.InetAddress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ExportMetricWriter;
import org.springframework.boot.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.actuate.endpoint.MetricsEndpointMetricReader;
import org.springframework.boot.actuate.metrics.jmx.JmxMetricWriter;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.quigley.zabbixj.agent.ZabbixAgent;

import io.micrometer.jmx.JmxConfig;
import io.micrometer.spring.web.servlet.WebMvcTagsProvider;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
@ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsConfig extends WebMvcConfigurerAdapter {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new MetricsInterceptor());
	}

	@Bean
	@ExportMetricWriter
	public MetricWriter metricWriter(MBeanExporter exporter) {
		return new JmxMetricWriter(exporter);
	}

	@Bean
	public MetricsEndpointMetricReader metricsEndpointMetricReader(MetricsEndpoint metricsEndpoint) {
		return new MetricsEndpointMetricReader(metricsEndpoint);
	}

	@Bean
	public WebMvcTagsProvider webmvcTagConfigurer() {
		return new CustomWebMvcTagsProvider();
	}

	@Bean
	@Autowired
	public EndpointMetricsProvider endpointMetricsProvider(JmxConfig config, MetricsProperties metricsProperties) {
		return new EndpointMetricsProvider(config, metricsProperties);
	}

	@Bean
	@Autowired
	public ZabbixAgent zabbixAgent(EndpointMetricsProvider endpointMetricsProvider, MetricsProperties metricsProperties) throws Exception {
		ZabbixAgent agent = new ZabbixAgent();
		agent.setEnableActive(true);
		agent.setEnablePassive(false);
		agent.setListenPort(metricsProperties.getZabbix().getListenPort());
		agent.setHostName(InetAddress.getLocalHost().getHostName());
		agent.setServerAddress(InetAddress.getByName(metricsProperties.getZabbix().getServerHost()));
		agent.setServerPort(metricsProperties.getZabbix().getServerPort());
		agent.setRefreshInterval(60);
		agent.addProvider("endpoint", endpointMetricsProvider);
		agent.addProvider("counter", new CounterMetricsProvider());
		agent.start();
		return agent;
	}
}
