package com.navent.realestate.trace;

import com.navent.realestate.metrics.NaventMetricsProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NaventMetricsProperties.class)
@ConditionalOnProperty(name = "metrics.trace.enabled", havingValue = "true", matchIfMissing = false)
public class TraceConfig {

//	@Bean
//	public io.opentracing.Tracer tracer(NaventMetricsProperties metricsProperties) {
//		Assert.notNull(metricsProperties.getTrace().getSenderEndpoint(), "Metrics Trace sender endpoint is mandatory");
//		Assert.notNull(metricsProperties.getTrace().getServiceName(), "Metrics Trace service name is mandatory");
//
//		Sender sender = URLConnectionSender.create(metricsProperties.getTrace().getSenderEndpoint());
//	    AsyncReporter<Span> reporter = AsyncReporter.builder(sender).build();
//	    Tracing braveTracer = Tracing.newBuilder().localServiceName(metricsProperties.getTrace().getServiceName())
//	    		.spanReporter(reporter).build();
//	    Tracer tracer = BraveTracer.create(braveTracer);
//	    return tracer;
//	}
}
