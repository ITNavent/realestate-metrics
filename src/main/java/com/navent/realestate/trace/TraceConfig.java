package com.navent.realestate.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import com.navent.realestate.metrics.MetricsProperties;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.Tracer;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
@ConditionalOnProperty(name = "metrics.trace.enabled", havingValue = "true", matchIfMissing = false)
public class TraceConfig {

	@Bean
	public io.opentracing.Tracer tracer(MetricsProperties metricsProperties) {
		Assert.notNull(metricsProperties.getTrace().getSenderEndpoint(), "Metrics Trace sender endpoint is mandatory");
		Assert.notNull(metricsProperties.getTrace().getServiceName(), "Metrics Trace service name is mandatory");

		Sender sender = URLConnectionSender.create(metricsProperties.getTrace().getSenderEndpoint());
	    AsyncReporter<Span> reporter = AsyncReporter.builder(sender).build();
	    Tracing braveTracer = Tracing.newBuilder().localServiceName(metricsProperties.getTrace().getServiceName())
	    		.spanReporter(reporter).build();
	    Tracer tracer = BraveTracer.create(braveTracer);
	    return tracer;
	}
}
