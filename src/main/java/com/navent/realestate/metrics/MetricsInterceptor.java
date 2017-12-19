package com.navent.realestate.metrics;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

public class MetricsInterceptor extends HandlerInterceptorAdapter {

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		Double status = response.getStatus() / 100d;
		Metrics.counter("http.response.status", Arrays.asList(Tag.of("status", String.format("%dXX", status.intValue())))).increment();
	}
}
