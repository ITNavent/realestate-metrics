package com.navent.realestate.metrics.meter;

import java.time.Duration;

import com.codahale.metrics.Meter;
import com.github.rollingmetrics.counter.SmoothlyDecayingRollingCounter;

public class SmoothlyDecayingRollingCounterMeter extends Meter {
	private SmoothlyDecayingRollingCounter windowCounter;

	public SmoothlyDecayingRollingCounterMeter(Duration rollingWindow, int numberChunks) {
		windowCounter = new SmoothlyDecayingRollingCounter(rollingWindow, numberChunks);
	}

	@Override
	public long getCount() {
		return windowCounter.getSum();
	}

	@Override
	public void mark(long n) {
		windowCounter.add(n);
	}
}
