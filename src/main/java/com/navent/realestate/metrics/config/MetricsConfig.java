package com.navent.realestate.metrics.config;

import com.navent.realestate.metrics.CustomWebMvcTagsProvider;
import com.navent.realestate.metrics.filter.CustomWebMvcMetricsFilter;
import com.navent.realestate.metrics.filter.MetricsInterceptor;
import com.navent.realestate.metrics.zabbixj.CounterMetricsProvider;
import com.navent.realestate.metrics.zabbixj.EndpointMetricsProvider;
import com.navent.realestate.metrics.zabbixj.GaugeMetricsProvider;
import com.quigley.zabbixj.agent.ZabbixAgent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties.Web.Server;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.DispatcherType;

import java.net.InetAddress;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.NaventJmxMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
@ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new MetricsInterceptor());
	}

	@Bean
	public WebMvcTagsProvider webmvcTagConfigurer() {
		return new CustomWebMvcTagsProvider();
	}

	@Bean
    public JmxMeterRegistry jmxMeterRegistry(JmxConfig config, Clock clock) {
		HierarchicalNameMapper nameMapper = HierarchicalNameMapper.DEFAULT;
        return new NaventJmxMeterRegistry(config, nameMapper, clock);
    }

	@Bean
	public FilterRegistrationBean<CustomWebMvcMetricsFilter> customWebMvcMetricsFilter(MeterRegistry registry, 
			MetricsProperties properties, WebMvcTagsProvider tagsProvider, WebApplicationContext context, NaventMetricsProperties metricsProperties) {
		Server serverProperties = properties.getWeb().getServer();
		CustomWebMvcMetricsFilter filter = new CustomWebMvcMetricsFilter(context, registry,
				tagsProvider, serverProperties.getRequestsMetricName(),
				serverProperties.isAutoTimeRequests(), metricsProperties);
		FilterRegistrationBean<CustomWebMvcMetricsFilter> registration = new FilterRegistrationBean<>(
				filter);
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
		return registration;
	}

	@Bean
	@Autowired
	public EndpointMetricsProvider endpointMetricsProvider(JmxConfig config, NaventMetricsProperties metricsProperties) {
		if(metricsProperties.getApdex().isEnabled()) {
			Assert.notNull(metricsProperties.getApdex().getMillis(), "Metrics apdex satisfied is mandatory with apdex enabled");
		}
		return new EndpointMetricsProvider(config, metricsProperties);
	}

	@Bean
	@Autowired
	public ZabbixAgent zabbixAgent(EndpointMetricsProvider endpointMetricsProvider, NaventMetricsProperties metricsProperties) throws Exception {
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
