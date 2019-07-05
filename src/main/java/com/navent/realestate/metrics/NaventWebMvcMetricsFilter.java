package com.navent.realestate.metrics;

import com.codahale.metrics.Meter;
import com.navent.realestate.metrics.NaventMetricsProperties.Apdex;
import com.navent.realestate.metrics.meter.SmoothlyDecayingRollingCounterMeter;

import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.ObjectUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.handler.MatchableHandlerMapping;
import org.springframework.web.util.NestedServletException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;

@NonNullApi
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class NaventWebMvcMetricsFilter extends OncePerRequestFilter {
	private static final String TIMING_SAMPLE = "navent.micrometer.timingSample";

	private final MeterRegistry registry;
	private final WebMvcTagsProvider tagsProvider;
	private final String metricName;
	private final boolean recordAsPercentiles;
	private final HandlerMappingIntrospector mappingIntrospector;
	private final NaventMetricsProperties naventProperties;
	
	private Timer appTimer;
	private Meter appRequestRateCounter;
	private Meter appResponseOkCounter;
	private Meter appResponseNokCounter;

	private Meter decayingAppApdexSatisfied;
	private Meter decayingAppApdexTotal;
	private Meter decayingAppApdexTolerating;

	private Apdex apdex;
	private Long apdexToleratingLimit;

	public NaventWebMvcMetricsFilter(MeterRegistry registry, WebMvcTagsProvider tagsProvider, String metricName,
			boolean recordAsPercentiles, HandlerMappingIntrospector mappingIntrospector,
			NaventMetricsProperties naventProperties) {
		this.registry = registry;

		this.tagsProvider = tagsProvider;
		this.metricName = metricName;
		this.recordAsPercentiles = recordAsPercentiles;
		this.mappingIntrospector = mappingIntrospector;
		this.naventProperties = naventProperties;

		createMetrics();
	}

	private void createMetrics() {
		TimerConfig rootTimerConfig = new TimerConfig(getServerRequestName() + ".uri.root", this.recordAsPercentiles);
		appTimer = getAppTimerBuilder(rootTimerConfig).register(this.registry);

		appRequestRateCounter = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
		FunctionCounter.builder(getServerRequestName() + ".uri.root.1.min.request.rate", appRequestRateCounter, c -> c.getCount())
			.description("App 1 minute request rate")
			.register(this.registry);

		appResponseOkCounter = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
		appResponseNokCounter = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);

		FunctionCounter.builder(getServerRequestName() + ".uri.root.response.ok", appResponseOkCounter, c -> c.getCount())
				.description("App response window counter")
				.register(this.registry);
		FunctionCounter.builder(getServerRequestName() + ".uri.root.response.nok", appResponseNokCounter, c -> c.getCount())
				.description("App response window counter")
				.register(this.registry);

		if (naventProperties != null) {
			apdex = naventProperties.getApdex();

			if (apdex.isEnabled()) {
				apdexToleratingLimit = apdex.getMillis() * 4;
				decayingAppApdexSatisfied = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
				decayingAppApdexTolerating = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);
				decayingAppApdexTotal = new SmoothlyDecayingRollingCounterMeter(Duration.ofSeconds(60), 10);

				FunctionCounter.builder(getServerRequestName() + ".uri.root.apdex.satisfied", decayingAppApdexSatisfied, c -> c.getCount())
						.description("App apdex satisfied window counter")
						.register(this.registry);
				FunctionCounter.builder(getServerRequestName() + ".uri.root.apdex.tolerating", decayingAppApdexTolerating, c -> c.getCount())
						.description("App apdex tolerating window counter").register(this.registry);
				FunctionCounter.builder(getServerRequestName() + ".uri.root.apdex.total", decayingAppApdexTotal, c -> c.getCount())
						.description("App apdex total window counter")
						.register(this.registry);
			}
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HandlerExecutionChain handler = null;
		try {
			MatchableHandlerMapping matchableHandlerMapping = mappingIntrospector.getMatchableHandlerMapping(request);
			if (matchableHandlerMapping != null) {
				handler = matchableHandlerMapping.getHandler(request);
			}
		} catch (Exception e) {
			logger.debug("Unable to time request", e);
			filterChain.doFilter(request, response);
			return;
		}

		final Object handlerObject = handler == null ? null : handler.getHandler();

		// If this is the second invocation of the filter in an async request, we don't
		// want to start sampling again (effectively bumping the active count on any
		// long task timers).
		// Rather, we'll just use the sampling context we started on the first
		// invocation.
		TimingSampleContext timingContext = (TimingSampleContext) request.getAttribute(TIMING_SAMPLE);
		if (timingContext == null) {
			timingContext = new TimingSampleContext(request, handlerObject);
		}

		try {
			filterChain.doFilter(request, response);

			if (request.isAsyncSupported()) {
				// this won't be "started" until after the first call to doFilter
				if (request.isAsyncStarted()) {
					request.setAttribute(TIMING_SAMPLE, timingContext);
				}
			}

			if (!request.isAsyncStarted()) {
				record(timingContext, response, request, handlerObject,
						(Throwable) request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE));
			}
		} catch (NestedServletException e) {
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			record(timingContext, response, request, handlerObject, e.getCause());
			throw e;
		} catch (ServletException | IOException | RuntimeException ex) {
			record(timingContext, response, request, handlerObject, ex);
			throw ex;
		}
	}

	private void record(TimingSampleContext timingContext, HttpServletResponse response, HttpServletRequest request,
			Object handlerObject, Throwable cause) {
		long appTime = timingContext.timerSample.stop(appTimer);
		appRequestRateCounter.mark();

		Meter appResponseCounter = (cause == null) ? appResponseOkCounter : appResponseNokCounter;
		appResponseCounter.mark();

		if (apdex.isEnabled()) {
			decayingAppApdexTotal.mark();
			if (appTime <= apdex.getMillis()) {
				decayingAppApdexSatisfied.mark();
			} else if (appTime > apdex.getMillis() && appTime <= apdexToleratingLimit) {
				decayingAppApdexTolerating.mark();
			}
		}
	}

	private Timer.Builder getAppTimerBuilder(TimerConfig config) {
		Timer.Builder builder = Timer.builder(config.getName())
				.description("Timer of app servlet request")
				.publishPercentileHistogram(config.histogram);
		if (config.getPercentiles().length > 0) {
			builder = builder.publishPercentiles(config.getPercentiles());
		}
		return builder;
	}

	private String getServerRequestName() {
		return this.metricName;
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
			this.extraTags = Tags.of(timed.extraTags());
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

	private class TimingSampleContext {
		private final Set<Timed> timedAnnotations;
		private final Timer.Sample timerSample;
		private final Collection<LongTaskTimer.Sample> longTaskTimerSamples;

		TimingSampleContext(HttpServletRequest request, Object handlerObject) {
			timedAnnotations = annotations(handlerObject);
			timerSample = Timer.start(registry);
			longTaskTimerSamples = timedAnnotations
					.stream().filter(Timed::longTask).map(t -> LongTaskTimer.builder(t)
							.tags(tagsProvider.getLongRequestTags(request, handlerObject)).register(registry).start())
					.collect(Collectors.toList());
		}

		private Set<Timed> annotations(Object handler) {
			if (handler instanceof HandlerMethod) {
				HandlerMethod handlerMethod = (HandlerMethod) handler;
				Set<Timed> timed = TimedUtils.findTimedAnnotations(handlerMethod.getMethod());
				if (timed.isEmpty()) {
					return TimedUtils.findTimedAnnotations(handlerMethod.getBeanType());
				}
				return timed;
			}
			return Collections.emptySet();
		}
	}
}