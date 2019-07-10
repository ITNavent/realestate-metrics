package com.navent.realestate.metrics;

import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;

public final class TimedUtils {
	private TimedUtils() {
    }

    public static Set<Timed> findTimedAnnotations(AnnotatedElement element) {
        Timed t = AnnotationUtils.findAnnotation(element, Timed.class);
        //noinspection ConstantConditions
        if (t != null)
            return Collections.singleton(t);

        TimedSet ts = AnnotationUtils.findAnnotation(element, TimedSet.class);
        if (ts != null) {
            return Arrays.stream(ts.value()).collect(Collectors.toSet());
        }

        return Collections.emptySet();
    }
}
