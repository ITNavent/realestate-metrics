package io.micrometer.core.instrument.dropwizard;

import com.navent.realestate.metrics.meter.SmoothlyDecayingRollingCounterMeter;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
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
	
	private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }
}
