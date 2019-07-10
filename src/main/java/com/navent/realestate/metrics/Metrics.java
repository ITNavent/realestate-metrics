package com.navent.realestate.metrics;

import com.navent.realestate.metrics.zabbixj.ZabbixRegisteredMetric;
import com.navent.realestate.metrics.zabbixj.ZabbixRegisteredMetricType;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;

public class Metrics {
	private static MeterRegistry meterRegistry;
	
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
    	Gauge g = Gauge.builder(name, number, Number::doubleValue).tags(tags).register(meterRegistry);
        String hierarchicalName = HierarchicalNameMapper.DEFAULT.toHierarchicalName(g.getId(), NamingConvention.camelCase);
        customMetricRegistry.add(new ZabbixRegisteredMetric(hierarchicalName, ZabbixRegisteredMetricType.gauge));
        return number;
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code f} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code f} on the object should be thread safe.
     * <p>
     * If multiple gauges are registered with the same id, then the values will be aggregated and
     * the sum will be reported. For example, registering multiple gauges for active threads in
     * a thread pool with the same id would produce a value that is the overall number
     * of active threads. For other behaviors, manage it on the user side and avoid multiple
     * registrations.
     *
     * @param name          Name of the gauge being registered.
     * @param tags          Sequence of dimensions for breaking down the name.
     * @param obj           Object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public static <T> Double gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> valueFunction) {
    	Gauge g = Gauge.builder(name, obj, valueFunction).tags(tags).register(meterRegistry);
        String hierarchicalName = HierarchicalNameMapper.DEFAULT.toHierarchicalName(g.getId(), NamingConvention.camelCase);
        customMetricRegistry.add(new ZabbixRegisteredMetric(hierarchicalName, ZabbixRegisteredMetricType.gauge));
        return g.value();
    }

    public static Set<ZabbixRegisteredMetric> getCustomMetricRegistryView() {
		return Collections.unmodifiableSet(customMetricRegistry);
	}
}
