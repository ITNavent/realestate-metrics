package io.micrometer.spring.web.servlet;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import com.navent.realestate.metrics.MetricsProperties;
import com.navent.realestate.metrics.MetricsProperties.Apdex;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.dropwizard.NaventJmxMeterRegistry;
import io.micrometer.spring.TimedUtils;

@Component
public class WebMvcMetrics {
	private static final String TIMING_REQUEST_ATTRIBUTE = "micrometer.requestStartTime";

	private static final String HANDLER_REQUEST_ATTRIBUTE = "micrometer.requestHandler";

	private static final String EXCEPTION_ATTRIBUTE = "micrometer.requestException";

	private static final Log logger = LogFactory.getLog(WebMvcMetrics.class);

	private final Map<HttpServletRequest, Long> longTaskTimerIds = Collections.synchronizedMap(new IdentityHashMap<>());

	private final MeterRegistry registry;

	private final WebMvcTagsProvider tagsProvider;

	private final String metricName;

	private final boolean autoTimeRequests;

	private final boolean recordAsPercentiles;

	private final Timer appTimer;
	private Counter appApdexSatisfied;
	private Counter appApdexTolerating;
	private Counter appApdexTotal;

	private Counter appResponseOkCounter;
	private Counter appResponseNokCounter;

	final static Iterable<Tag> appTags = Arrays.asList(Tag.of("uri", "root"));

	private Apdex apdex;
	private Long apdexToleratingLimit;

	public WebMvcMetrics(MeterRegistry registry, WebMvcTagsProvider tagsProvider,
                         String metricName, boolean autoTimeRequests, boolean recordAsPercentiles) {
        this.registry = registry;

        this.tagsProvider = tagsProvider;
        this.metricName = metricName;
        this.autoTimeRequests = autoTimeRequests;
        this.recordAsPercentiles = recordAsPercentiles;

        TimerConfig rootTimerConfig = new TimerConfig(getServerRequestName(), this.recordAsPercentiles);
        appTimer = getAppTimerBuilder(rootTimerConfig).register(this.registry);

        appResponseOkCounter = Counter.builder(getServerRequestName())
        		.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("response", "ok"))).description("App response window counter")
        		.register(this.registry);
        appResponseNokCounter = Counter.builder(getServerRequestName())
        		.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("response", "nok"))).description("App response window counter")
        		.register(this.registry);

        MetricsProperties props = getMetricsProperties();
        if(props != null) {
        	apdex = props.getApdex();

        	if(apdex.isEnabled()) {
        		apdexToleratingLimit = apdex.getMillis() * 4;
        		appApdexSatisfied = Counter.builder(getServerRequestName())
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "satisfied")))
        				.description("App apdex satisfied window counter").register(this.registry);
        		appApdexTolerating = Counter.builder(getServerRequestName())
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "tolerating")))
        				.description("App apdex tolerating window counter").register(this.registry);
        		appApdexTotal = Counter.builder(getServerRequestName())
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "total")))
        				.description("App apdex total window counter").register(this.registry);
        	}
        }
    }

	public void tagWithException(Throwable exception) {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		attributes.setAttribute(EXCEPTION_ATTRIBUTE, exception, RequestAttributes.SCOPE_REQUEST);
	}

	void preHandle(HttpServletRequest request, Object handler) {
		request.setAttribute(TIMING_REQUEST_ATTRIBUTE, System.nanoTime());
		request.setAttribute(HANDLER_REQUEST_ATTRIBUTE, handler);
		longTaskTimed(handler).forEach((config) -> {
			if (config.getName() == null) {
				logWarning(request, handler);
				return;
			}
			this.longTaskTimerIds.put(request, longTaskTimer(config, request, handler).start());
		});
	}

	private void logWarning(HttpServletRequest request, Object handler) {
		if (handler instanceof HandlerMethod) {
			logger.warn("Unable to perform metrics timing on " + ((HandlerMethod) handler).getShortLogMessage()
					+ ": @Timed annotation must have a value used to name the metric");
			return;
		}
		logger.warn("Unable to perform metrics timing for request " + request.getRequestURI()
				+ ": @Timed annotation must have a value used to name the metric");
	}

	void record(HttpServletRequest request, HttpServletResponse response, Throwable ex) {
		Object handler = request.getAttribute(HANDLER_REQUEST_ATTRIBUTE);
		Long startTime = (Long) request.getAttribute(TIMING_REQUEST_ATTRIBUTE);
		long endTime = System.nanoTime();
		completeLongTimerTasks(request, handler);
		Throwable thrown = (ex != null ? ex : (Throwable) request.getAttribute(EXCEPTION_ATTRIBUTE));
		recordTimerTasks(request, response, handler, startTime, endTime, thrown);
	}

	private void completeLongTimerTasks(HttpServletRequest request, Object handler) {
		longTaskTimed(handler).forEach((config) -> completeLongTimerTask(request, handler, config));
	}

	private void completeLongTimerTask(HttpServletRequest request, Object handler, TimerConfig config) {
		if (config.getName() != null) {
			Long timerId = this.longTaskTimerIds.remove(request);
			LongTaskTimer longTaskTimer = longTaskTimer(config, request, handler);

			if (timerId != null) {
				longTaskTimer.stop(timerId);
			}
		}
	}

	private void recordTimerTasks(HttpServletRequest request, HttpServletResponse response, Object handler,
			Long startTime, long endTime, Throwable thrown) {
		long amount = endTime - startTime;
		timed(handler).forEach((config) -> {
			getTimerBuilder(request, response, thrown, config).register(this.registry).record(amount, TimeUnit.NANOSECONDS);
			getCounterBuilder(request, response, thrown).register(this.registry).increment();

			appTimer.record(amount, TimeUnit.NANOSECONDS);

			Counter appResponseCounter = (thrown==null)? appResponseOkCounter: appResponseNokCounter;
			appResponseCounter.increment();

			if(apdex.isEnabled()) {
				appApdexTotal.increment();
				if(amount <= apdex.getMillis()) {
					appApdexSatisfied.increment();
				} else if(amount > apdex.getMillis() && amount <= apdexToleratingLimit) {
					appApdexTolerating.increment();
				}
			}
		});
	}

	private Timer.Builder getTimerBuilder(HttpServletRequest request, HttpServletResponse response, Throwable thrown,
			TimerConfig config) {
		Timer.Builder builder = Timer.builder(config.getName())
				.tags(this.tagsProvider.httpRequestTags(request, response, thrown)).tags(config.getExtraTags())
				.description("Timer of servlet request").publishPercentileHistogram(config.histogram);
		if (config.getPercentiles().length > 0) {
			builder = builder.publishPercentiles(config.getPercentiles());
		}
		return builder;
	}

	private Counter.Builder getCounterBuilder(HttpServletRequest request, HttpServletResponse response, Throwable thrown) {
		Counter.Builder builder = Counter.builder(getServerRequestName())
				.tags(this.tagsProvider.httpRequestTags(request, response, thrown))
				.tags(Tags.of("response", (thrown == null)?"ok":"nok"))
				.description("Window counter of servlet ok request");
		return builder;
	}

	private Timer.Builder getAppTimerBuilder(TimerConfig config) {
		Timer.Builder builder = Timer.builder(config.getName()).tags(appTags)
				.description("Timer of app servlet request").publishPercentileHistogram(config.histogram);
		if (config.getPercentiles().length > 0) {
			builder = builder.publishPercentiles(config.getPercentiles());
		}
		return builder;
	}

	private LongTaskTimer longTaskTimer(TimerConfig config, HttpServletRequest request, Object handler) {
		return LongTaskTimer.builder(config.getName()).tags(this.tagsProvider.httpLongRequestTags(request, handler))
				.tags(config.getExtraTags()).description("Timer of long servlet request").register(registry);
	}

	private Set<TimerConfig> longTaskTimed(Object handler) {
		if (handler instanceof HandlerMethod) {
			return longTaskTimed((HandlerMethod) handler);
		}
		return Collections.emptySet();
	}

	private Set<TimerConfig> longTaskTimed(HandlerMethod handler) {
		Set<TimerConfig> timed = getLongTaskAnnotationConfig(handler.getMethod());
		if (timed.isEmpty()) {
			return getLongTaskAnnotationConfig(handler.getBeanType());
		}
		return timed;
	}

	private Set<TimerConfig> timed(Object handler) {
		if (handler instanceof HandlerMethod) {
			return timed((HandlerMethod) handler);
		} else if (handler instanceof ResourceHttpRequestHandler && this.autoTimeRequests) {
			return Collections.singleton(new TimerConfig(getServerRequestName(), this.recordAsPercentiles));
		}
		return Collections.emptySet();
	}

	private Set<TimerConfig> timed(HandlerMethod handler) {
		Set<TimerConfig> config = getShortTaskAnnotationConfig(handler.getMethod());
		if (config.isEmpty()) {
			config = getShortTaskAnnotationConfig(handler.getBeanType());
			if (config.isEmpty() && this.autoTimeRequests) {
				return Collections.singleton(new TimerConfig(getServerRequestName(), this.recordAsPercentiles));
			}
		}
		return config;
	}

	private Set<TimerConfig> getShortTaskAnnotationConfig(AnnotatedElement element) {
		return TimedUtils.findTimedAnnotations(element).filter((t) -> !t.longTask()).map(this::fromAnnotation)
				.collect(Collectors.toSet());
	}

	private Set<TimerConfig> getLongTaskAnnotationConfig(AnnotatedElement element) {
		return TimedUtils.findTimedAnnotations(element).filter(Timed::longTask).map(this::fromAnnotation)
				.collect(Collectors.toSet());
	}

	private TimerConfig fromAnnotation(Timed timed) {
		return new TimerConfig(timed, this::getServerRequestName);
	}

	private String getServerRequestName() {
		return this.metricName;
	}

	private MetricsProperties getMetricsProperties() {
		if(registry instanceof CompositeMeterRegistry) {
			for (MeterRegistry reg : ((CompositeMeterRegistry) registry).getRegistries()) {
				if(reg instanceof NaventJmxMeterRegistry) {
					return ((NaventJmxMeterRegistry) reg).getMetricsProperties();
				}
			}
		}
		return null;
	}

	private static class TimerConfig {

		private final String name;

		private final Iterable<Tag> extraTags;

		private final double[] percentiles;

		private final boolean histogram;

		TimerConfig(String name, boolean histogram) {
			this.name = name;
			this.extraTags = Collections.emptyList();
			this.percentiles = new double[0];
			this.histogram = histogram;
		}

		TimerConfig(Timed timed, Supplier<String> name) {
			this.name = buildName(timed, name);
			this.extraTags = Tags.zip(timed.extraTags());
			this.percentiles = timed.percentiles();
			this.histogram = timed.histogram();
		}

		private String buildName(Timed timed, Supplier<String> name) {
			if (timed.longTask() && timed.value().isEmpty()) {
				// the user MUST name long task timers, we don't lump them in
				// with regular
				// timers with the same name
				return null;
			}
			return (timed.value().isEmpty() ? name.get() : timed.value());
		}

		public String getName() {
			return this.name;
		}

		Iterable<Tag> getExtraTags() {
			return this.extraTags;
		}

		double[] getPercentiles() {
			return this.percentiles;
		}

		boolean isHistogram() {
			return this.histogram;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TimerConfig other = (TimerConfig) o;
			return ObjectUtils.nullSafeEquals(this.name, other.name);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHashCode(this.name);
		}
	}
}
