package com.navent.realestate.metrics.filter;

import com.navent.realestate.metrics.NaventMetricsProperties;
import com.navent.realestate.metrics.NaventMetricsProperties.Apdex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.handler.MatchableHandlerMapping;
import org.springframework.web.util.NestedServletException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Builder;
import io.micrometer.core.instrument.Timer.Sample;

/**
 * Intercepts incoming HTTP requests and records metrics about Spring MVC execution time
 * and results.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @since 2.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CustomWebMvcMetricsFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory
			.getLogger(CustomWebMvcMetricsFilter.class);

	private final ApplicationContext context;

	private final MeterRegistry registry;

	private final WebMvcTagsProvider tagsProvider;

	private final String metricName;

	private final boolean autoTimeRequests;

	private volatile HandlerMappingIntrospector introspector;

	final static Iterable<Tag> appTags = Arrays.asList(Tag.of("uri", "root"));
	private NaventMetricsProperties metricsProperties;

	private final Timer appTimer;
	private Counter appApdexSatisfied;
	private Counter appApdexTolerating;
	private Counter appApdexTotal;

	private Counter appResponseOkCounter;
	private Counter appResponseNokCounter;

	private Apdex apdex;
	private Long apdexToleratingLimit;

	/**
	 * Create a new {@link CustomWebMvcMetricsFilter} instance.
	 * @param context the source application context
	 * @param registry the meter registry
	 * @param tagsProvider the tags provider
	 * @param metricName the metric name
	 * @param autoTimeRequests if requests should be automatically timed
	 */
	public CustomWebMvcMetricsFilter(ApplicationContext context, MeterRegistry registry,
			WebMvcTagsProvider tagsProvider, String metricName,
			boolean autoTimeRequests) {
		this.context = context;
		this.registry = registry;
		this.tagsProvider = tagsProvider;
		this.metricName = metricName;
		this.autoTimeRequests = autoTimeRequests;

		appTimer = getAppTimerBuilder(this.metricName, appTags, false).register(this.registry);
		appResponseOkCounter = Counter.builder(this.metricName)
        		.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("response", "ok"))).description("App response window counter")
        		.register(this.registry);
        appResponseNokCounter = Counter.builder(this.metricName)
        		.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("response", "nok"))).description("App response window counter")
        		.register(this.registry);

        if(metricsProperties != null) {
        	apdex = metricsProperties.getApdex();

        	if(apdex.isEnabled()) {
        		apdexToleratingLimit = apdex.getMillis() * 4;
        		appApdexSatisfied = Counter.builder(this.metricName)
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "satisfied")))
        				.description("App apdex satisfied window counter").register(this.registry);
        		appApdexTolerating = Counter.builder(this.metricName)
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "tolerating")))
        				.description("App apdex tolerating window counter").register(this.registry);
        		appApdexTotal = Counter.builder(this.metricName)
        				.tags(Arrays.asList(Tag.of("uri", "root"), Tag.of("apdex", "total")))
        				.description("App apdex total window counter").register(this.registry);
        	}
        }
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		filterAndRecordMetrics(request, response, filterChain);
	}

	private void filterAndRecordMetrics(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws IOException, ServletException {
		Object handler;
		try {
			handler = getHandler(request);
		}
		catch (Exception ex) {
			logger.debug("Unable to time request", ex);
			filterChain.doFilter(request, response);
			return;
		}
		filterAndRecordMetrics(request, response, filterChain, handler);
	}

	private Object getHandler(HttpServletRequest request) throws Exception {
		HttpServletRequest wrapper = new UnmodifiableAttributesRequestWrapper(request);
		for (HandlerMapping mapping : getMappingIntrospector().getHandlerMappings()) {
			HandlerExecutionChain chain = mapping.getHandler(wrapper);
			if (chain != null) {
				if (mapping instanceof MatchableHandlerMapping) {
					return chain.getHandler();
				}
				return null;
			}
		}
		return null;
	}

	private HandlerMappingIntrospector getMappingIntrospector() {
		if (this.introspector == null) {
			this.introspector = this.context.getBean(HandlerMappingIntrospector.class);
		}
		return this.introspector;
	}

	private void filterAndRecordMetrics(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain, Object handler)
			throws IOException, ServletException {
		TimingContext timingContext = TimingContext.get(request);
		if (timingContext == null) {
			timingContext = startAndAttachTimingContext(request, handler);
		}
		try {
			filterChain.doFilter(request, response);
			if (!request.isAsyncStarted()) {
				// Only record when async processing has finished or never been started.
				// If async was started by something further down the chain we wait
				// until the second filter invocation (but we'll be using the
				// TimingContext that was attached to the first)
				Throwable exception = (Throwable) request
						.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
				record(timingContext, response, request, handler, exception);
			}
		}
		catch (NestedServletException ex) {
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			record(timingContext, response, request, handler, ex.getCause());
			throw ex;
		}
	}

	private TimingContext startAndAttachTimingContext(HttpServletRequest request,
			Object handler) {
		Set<Timed> annotations = getTimedAnnotations(handler);
		Timer.Sample timerSample = Timer.start(this.registry);
		Collection<LongTaskTimer.Sample> longTaskTimerSamples = getLongTaskTimerSamples(
				request, handler, annotations);
		TimingContext timingContext = new TimingContext(annotations, timerSample,
				longTaskTimerSamples);
		timingContext.attachTo(request);
		return timingContext;
	}

	private Set<Timed> getTimedAnnotations(Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return Collections.emptySet();
		}
		return getTimedAnnotations((HandlerMethod) handler);
	}

	private Set<Timed> getTimedAnnotations(HandlerMethod handler) {
		Set<Timed> timed = findTimedAnnotations(handler.getMethod());
		if (timed.isEmpty()) {
			return findTimedAnnotations(handler.getBeanType());
		}
		return timed;
	}

	private Set<Timed> findTimedAnnotations(AnnotatedElement element) {
		return AnnotationUtils.getDeclaredRepeatableAnnotations(element, Timed.class);
	}

	private Collection<LongTaskTimer.Sample> getLongTaskTimerSamples(
			HttpServletRequest request, Object handler, Set<Timed> annotations) {
		List<LongTaskTimer.Sample> samples = new ArrayList<>();
		annotations.stream().filter(Timed::longTask).forEach((annotation) -> {
			Iterable<Tag> tags = this.tagsProvider.getLongRequestTags(request, handler);
			LongTaskTimer.Builder builder = LongTaskTimer.builder(annotation).tags(tags);
			LongTaskTimer timer = builder.register(this.registry);
			samples.add(timer.start());
		});
		return samples;
	}

	private void record(TimingContext timingContext, HttpServletResponse response,
			HttpServletRequest request, Object handlerObject, Throwable exception) {
		Timer.Sample timerSample = timingContext.getTimerSample();
		Supplier<Iterable<Tag>> tags = () -> this.tagsProvider.getTags(request, response,
				handlerObject, exception);
		/*for (Timed annotation : timingContext.getAnnotations()) {
			stop(timerSample, tags, Timer.builder(annotation, this.metricName));
		}
		if (timingContext.getAnnotations().isEmpty() && this.autoTimeRequests) {*/
			long duration = stop(timerSample, tags, Timer.builder(this.metricName));
			long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);
			recordCustomAppMetrics(durationMs, exception);
			recordCustomRequestMetrics(durationMs, response, request, handlerObject, exception);
		//}
		/*for (LongTaskTimer.Sample sample : timingContext.getLongTaskTimerSamples()) {
			sample.stop();
		}*/
	}

	private void recordCustomRequestMetrics(long durationMs, HttpServletResponse response,
			HttpServletRequest request, Object handlerObject, Throwable exception) {
		Counter.Builder builder = Counter.builder(this.metricName)
				.tags(this.tagsProvider.getTags(request, response, handlerObject, exception))
				.tags(Tags.of("response", (exception == null)? "ok": "nok"))
				.description("Window counter of servlet ok request");
		builder.register(this.registry);
	}

	@SuppressWarnings("resource")
	private void recordCustomAppMetrics(long durationMs, Throwable exception) {
		appTimer.record(durationMs, TimeUnit.MILLISECONDS);

		Counter appResponseCounter = (exception == null)? appResponseOkCounter: appResponseNokCounter;
		appResponseCounter.increment();

		if(apdex.isEnabled()) {
			appApdexTotal.increment();
			if(durationMs <= apdex.getMillis()) {
				appApdexSatisfied.increment();
			} else if(durationMs > apdex.getMillis() && durationMs <= apdexToleratingLimit) {
				appApdexTolerating.increment();
			}
		}

		
	}

	private long stop(Timer.Sample timerSample, Supplier<Iterable<Tag>> tags,
			Builder builder) {
		return timerSample.stop(builder.tags(tags.get()).register(this.registry));
	}

	/**
	 * Context object attached to a request to retain information across the multiple
	 * filter calls that happen with async requests.
	 */
	private static class TimingContext {

		private static final String ATTRIBUTE = TimingContext.class.getName();

		private final Set<Timed> annotations;

		private final Timer.Sample timerSample;

		private final Collection<LongTaskTimer.Sample> longTaskTimerSamples;

		TimingContext(Set<Timed> annotations, Sample timerSample,
				Collection<io.micrometer.core.instrument.LongTaskTimer.Sample> longTaskTimerSamples) {
			this.annotations = annotations;
			this.timerSample = timerSample;
			this.longTaskTimerSamples = longTaskTimerSamples;
		}

		public Set<Timed> getAnnotations() {
			return this.annotations;
		}

		public Timer.Sample getTimerSample() {
			return this.timerSample;
		}

		public Collection<LongTaskTimer.Sample> getLongTaskTimerSamples() {
			return this.longTaskTimerSamples;
		}

		public void attachTo(HttpServletRequest request) {
			request.setAttribute(ATTRIBUTE, this);
		}

		public static TimingContext get(HttpServletRequest request) {
			return (TimingContext) request.getAttribute(ATTRIBUTE);
		}

	}

	/**
	 * An {@link HttpServletRequestWrapper} that prevents modification of the request's
	 * attributes.
	 */
	private static final class UnmodifiableAttributesRequestWrapper
			extends HttpServletRequestWrapper {

		private UnmodifiableAttributesRequestWrapper(HttpServletRequest request) {
			super(request);
		}

		@Override
		public void setAttribute(String name, Object value) {
		}

	}

	private Timer.Builder getAppTimerBuilder(String name, Iterable<Tag> tags, boolean percentileHistogram) {
		Timer.Builder builder = Timer.builder(name).tags(tags)
				.description("Timer of app servlet request")
				.publishPercentileHistogram(percentileHistogram);
		return builder;
	}
}

