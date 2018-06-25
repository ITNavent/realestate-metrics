package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.navent.realestate.metrics.meter.SmoothlyDecayingRollingCounterMeter;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

public class NaventJmxMeterRegistry extends JmxMeterRegistry {
	private final DropwizardClock dropwizardClock;
    private final HierarchicalNameMapper nameMapper;

	public NaventJmxMeterRegistry(JmxConfig config) {
        this(config, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

	public NaventJmxMeterRegistry(JmxConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
		super(config, clock, nameMapper);
        this.dropwizardClock = new DropwizardClock(clock);
        this.nameMapper = nameMapper;

        this.config().namingConvention(NamingConvention.camelCase);
    }

	@Override
    protected Counter newCounter(Meter.Id id) {
    	com.codahale.metrics.Meter meter = null;
    	if(StringUtils.containsIgnoreCase(id.getDescription(), "window")) {
    		meter = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
    	} else {
    		meter = new com.codahale.metrics.Meter(dropwizardClock);
    	}
    	getDropwizardRegistry().register(hierarchicalName(id), meter);
        return new DropwizardCounter(id, meter);
    }

	@Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge gauge = null;
        if(Long.class.isAssignableFrom(obj.getClass())) {
        	gauge = () -> {
        		T obj2 = ref.get();
        		if (obj2 != null) {
        			return ((Long) obj2).longValue();
        		} else {
        			return nullGaugeValue();
        		}
            };
        } else {
        	gauge = () -> {
        		T obj2 = ref.get();
        		if (obj2 != null) {
        			return valueFunction.applyAsDouble(obj2);
        		} else {
        			return nullGaugeValue();
        		}
        	};
        }
        getDropwizardRegistry().register(hierarchicalName(id), gauge);
        return new DropwizardGauge(id, gauge);
    }

	private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }
}
