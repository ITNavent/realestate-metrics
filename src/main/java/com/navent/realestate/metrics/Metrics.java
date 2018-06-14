package com.navent.realestate.metrics;

import com.navent.realestate.metrics.zabbixj.ZabbixRegisteredMetric;
import com.navent.realestate.metrics.zabbixj.ZabbixRegisteredMetricType;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tag;
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

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
    	T g = io.micrometer.core.instrument.Metrics.gauge(name, tags, number);
    	Id id = new Id(name, tags, "", "", Type.GAUGE);
        String hierarchicalName = HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, NamingConvention.camelCase);
        customMetricRegistry.add(new ZabbixRegisteredMetric(hierarchicalName, ZabbixRegisteredMetricType.gauge));
        return g;
    }

    public static Set<ZabbixRegisteredMetric> getCustomMetricRegistryView() {
		return Collections.unmodifiableSet(customMetricRegistry);
	}
}
