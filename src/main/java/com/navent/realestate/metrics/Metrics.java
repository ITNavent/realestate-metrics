package com.navent.realestate.metrics;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class Metrics {
	private static final Set<ZabbixRegisteredMetric> customMetricRegistry = ConcurrentHashMap.newKeySet();

	/**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     */
    public static Counter counter(String name, String... tags) {
    	Counter c = io.micrometer.core.instrument.Metrics.counter(name, tags);
        String hierarchicalName = HierarchicalNameMapper.DEFAULT.toHierarchicalName(c.getId(), NamingConvention.camelCase);
        customMetricRegistry.add(new ZabbixRegisteredMetric(hierarchicalName, ZabbixRegisteredMetricType.counter));
        return c;
    }

    public static Set<ZabbixRegisteredMetric> getCustomMetricRegistryView() {
		return Collections.unmodifiableSet(customMetricRegistry);
	}
}
