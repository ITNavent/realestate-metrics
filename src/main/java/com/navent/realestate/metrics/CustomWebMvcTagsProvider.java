package com.navent.realestate.metrics;


import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.micrometer.core.instrument.Tag;
import io.micrometer.spring.web.servlet.WebMvcTags;
import io.micrometer.spring.web.servlet.WebMvcTagsProvider;

public class CustomWebMvcTagsProvider implements WebMvcTagsProvider {

	@Override
	public Iterable<Tag> httpLongRequestTags(HttpServletRequest request, Object handler) {
		return Arrays.asList(WebMvcTags.method(request), WebMvcTags.uri(request, null));
	}

	@Override
	public Iterable<Tag> httpRequestTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable ex) {
		return Arrays.asList(WebMvcTags.method(request), WebMvcTags.uri(request, response));
	}
}
