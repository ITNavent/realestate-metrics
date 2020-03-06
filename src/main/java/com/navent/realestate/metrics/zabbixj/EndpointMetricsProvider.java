package com.navent.realestate.metrics.zabbixj;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navent.realestate.metrics.NaventMetricsProperties;
import com.quigley.zabbixj.metrics.MetricsException;
import com.quigley.zabbixj.metrics.MetricsKey;
import com.quigley.zabbixj.metrics.MetricsProvider;

import io.micrometer.jmx.JmxConfig;
import lombok.val;

public class EndpointMetricsProvider implements MetricsProvider {

	private List<Pair<String, String>> endpoints = new LinkedList<>();
	private ObjectMapper mapper = new ObjectMapper();
	private Pattern pattern;

	public EndpointMetricsProvider(JmxConfig config, NaventMetricsProperties metricsProperties) {
		this.pattern = Pattern.compile(metricsProperties.getEndpoint().getPattern());
	}

	@EventListener
	public void handleContextRefresh(ContextRefreshedEvent event) {
		ApplicationContext applicationContext = ((ContextRefreshedEvent) event).getApplicationContext();
		Set<Map.Entry<RequestMappingInfo, HandlerMethod>> entries = new HashSet<>();
		Map<String, RequestMappingHandlerMapping> beansOfType = applicationContext.getBeansOfType(RequestMappingHandlerMapping.class);
		for (RequestMappingHandlerMapping value : beansOfType.values()) {
			entries.addAll(value.getHandlerMethods().entrySet());
		}
		endpoints.clear();
		entries.forEach(h -> {
			String uri = h.getKey().getPatternsCondition().getPatterns().iterator().next();
			if (pattern.matcher(uri).matches()) {
				String method = h.getKey().getMethodsCondition().getMethods().iterator().next().toString();
				endpoints.add(Pair.of(uri.replaceAll("\\*", ""), method));
			}
		});
	}

	@Override
	public Object getValue(MetricsKey key) throws MetricsException {
		if ("discovery".equals(key.getKey())) {
			Map<String, List<Map<String, Object>>> values = new HashMap<>();
			values.put("data", endpoints.stream().map(e -> {
				val entry = new HashMap<String, Object>(2);
				entry.put("{#ENDPOINTNAME}", e.getKey());
				entry.put("{#ENDPOINTMETHOD}", e.getValue());
				return entry;
			}).collect(Collectors.toList()));
			try {
				return mapper.writeValueAsString(values);
			} catch (JsonProcessingException e1) {
				throw new MetricsException(e1);
			}
		}
		return null;
	}
}
