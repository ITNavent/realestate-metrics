package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.navent.realestate.metrics.NaventMetricsProperties;
import com.navent.realestate.metrics.SmoothlyDecayingRollingCounterMeter;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import io.micrometer.jmx.JmxConfig;

public class NaventJmxMeterRegistry extends MeterRegistry {
	private final MetricRegistry registry;
    private final HierarchicalNameMapper nameMapper;
    private final DropwizardClock dropwizardClock;
    private final DropwizardConfig dropwizardConfig;
	private final JmxReporter reporter;
	private final NaventMetricsProperties metricsProperties;

    public NaventJmxMeterRegistry(JmxConfig config, NaventMetricsProperties metricsProperties) {
        this(config, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM, metricsProperties);
    }

    public NaventJmxMeterRegistry(JmxConfig config, HierarchicalNameMapper nameMapper, Clock clock, NaventMetricsProperties metricsProperties) {
    	super(clock);
        this.dropwizardConfig = config;
        this.dropwizardClock = new DropwizardClock(clock);
        this.registry = new MetricRegistry();
        this.nameMapper = nameMapper;
        this.metricsProperties = metricsProperties;
        this.config().namingConvention(NamingConvention.camelCase);

        this.reporter = JmxReporter.forRegistry(getDropwizardRegistry()).build();
        this.reporter.start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }

    public MetricRegistry getDropwizardRegistry() {
        return registry;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
    	com.codahale.metrics.Meter meter = null;
    	if(StringUtils.containsIgnoreCase(id.getDescription(), "window")) {
    		meter = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
    	} else {
    		meter = new com.codahale.metrics.Meter(dropwizardClock);
    	}
        registry.register(hierarchicalName(id), meter);
        return new DropwizardCounter(id, meter);
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge<Double> gauge = () -> {
            T obj2 = ref.get();
            if (obj2 != null) {
                return valueFunction.applyAsDouble(obj2);
            } else {
                return nullGaugeValue();
            }
        };
        registry.register(hierarchicalName(id), gauge);
        return new DropwizardGauge(id, gauge);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DropwizardTimer timer = new DropwizardTimer(id, registry.timer(hierarchicalName(id)), clock, distributionStatisticConfig, pauseDetector);
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DropwizardDistributionSummary summary = new DropwizardDistributionSummary(id, clock, registry.histogram(hierarchicalName(id)), distributionStatisticConfig, scale);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
        registry.register(hierarchicalName(id.withTag(Statistic.ACTIVE_TASKS)), (Gauge<Integer>) ltt::activeTasks);
        registry.register(hierarchicalName(id.withTag(Statistic.DURATION)), (Gauge<Double>) () -> ltt.duration(TimeUnit.NANOSECONDS));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        DropwizardFunctionTimer ft = new DropwizardFunctionTimer<>(id, clock, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnits, getBaseTimeUnit());
        registry.register(hierarchicalName(id), ft.getDropwizardMeter());
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        DropwizardFunctionCounter<T> fc = new DropwizardFunctionCounter<>(id, clock, obj, countFunction);
        registry.register(hierarchicalName(id), fc.getDropwizardMeter());
        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> registry.register(hierarchicalName(id.withTag(ms.getStatistic())), (Gauge<Double>) ms::getValue));
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(dropwizardConfig.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    public NaventMetricsProperties getMetricsProperties() {
		return metricsProperties;
	}

    protected Double nullGaugeValue() {
        return Double.NaN;
    }
}
