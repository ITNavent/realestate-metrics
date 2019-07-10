package com.navent.realestate.metrics.config;


import com.navent.realestate.metrics.CustomWebMvcTagsProvider;
import com.navent.realestate.metrics.NaventMetricsProperties;
import com.navent.realestate.metrics.NaventWebMvcMetricsFilter;
import com.navent.realestate.metrics.filter.MetricsInterceptor;
import com.navent.realestate.metrics.zabbixj.CounterMetricsProvider;
import com.navent.realestate.metrics.zabbixj.EndpointMetricsProvider;
import com.navent.realestate.metrics.zabbixj.GaugeMetricsProvider;
import com.quigley.zabbixj.agent.ZabbixAgent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.net.InetAddress;
import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;


@Configuration
@EnableConfigurationProperties(NaventMetricsProperties.class)
@ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new MetricsInterceptor());
	}

	@Bean
	public WebMvcTagsProvider servletTagsProvider() {
		return new CustomWebMvcTagsProvider();
	}

	public MeterRegistryCustomizer<JmxMeterRegistry> jmxMetricsNamingConvention() {
		return registry -> registry.config().namingConvention(NamingConvention.camelCase);
	}

	@Bean
	public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusMetricsNamingConvention(
			MetricsProperties properties) {
		return registry -> registry.config().meterFilter(MeterFilter.deny(new Predicate<Meter.Id>() {
			@Override
			public boolean test(Meter.Id id) {
				return !id.getName().equals(
						properties.getWeb().getServer().getRequestsMetricName() + ".uri.root.1.min.request.rate");
			}
		}));
	}

	@SuppressWarnings("deprecation")
	@Bean
	public NaventWebMvcMetricsFilter naventWebMetricsFilter(MeterRegistry registry, WebMvcTagsProvider tagsProvider,
			WebApplicationContext ctx, MetricsProperties properties, NaventMetricsProperties naventProperties) {
		return new NaventWebMvcMetricsFilter(registry, tagsProvider,
				properties.getWeb().getServer().getRequestsMetricName(), true, new HandlerMappingIntrospector(ctx),
				naventProperties);
	}

	@Bean
	@Autowired
	public EndpointMetricsProvider endpointMetricsProvider(JmxConfig config,
			NaventMetricsProperties metricsProperties) {
		if (metricsProperties.getApdex().isEnabled()) {
			Assert.notNull(metricsProperties.getApdex().getMillis(),
					"Metrics apdex satisfied is mandatory with apdex enabled");
		}
		return new EndpointMetricsProvider(config, metricsProperties);
	}

	@Bean
	@Autowired
	public ZabbixAgent zabbixAgent(EndpointMetricsProvider endpointMetricsProvider,
			NaventMetricsProperties metricsProperties) throws Exception {
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
		agent.addProvider("gauge", new GaugeMetricsProvider());
		agent.start();
		return agent;
	}
}
