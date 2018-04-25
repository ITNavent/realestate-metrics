package com.navent.realestate.metrics.zabbixj;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ZabbixRegisteredMetric {
	private String name;
	private ZabbixRegisteredMetricType type;
}
