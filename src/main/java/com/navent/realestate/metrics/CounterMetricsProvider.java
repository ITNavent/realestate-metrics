package com.navent.realestate.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quigley.zabbixj.metrics.MetricsException;
import com.quigley.zabbixj.metrics.MetricsKey;
import com.quigley.zabbixj.metrics.MetricsProvider;

import lombok.val;

/**
 * Informa a zabbix metricas del tipo counter que hayan sido agregadas a través de {@see com.navent.realestate.metrics.Metrics#counter(String, String...)}
 * @author bfalese
 */
public class CounterMetricsProvider implements MetricsProvider {

	private ObjectMapper mapper = new ObjectMapper();

	@Override
	public Object getValue(MetricsKey key) throws MetricsException {
		if ("discovery".equals(key.getKey())) {
			Map<String, List<Map<String, Object>>> values = new HashMap<>();
			values.put("data", Metrics.getCustomMetricRegistryView().stream()
				.filter(e -> e.getType().equals(ZabbixRegisteredMetricType.counter))
				.map(e -> {
					val entry = new HashMap<String, Object>(1);
					entry.put("{#COUNTERNAME}", e.getName());
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
