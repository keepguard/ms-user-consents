package com.keepguard.ms_user_consents.application.port.out.metrics;

import io.micrometer.core.instrument.Timer;

import java.util.Map;

public interface MetricsPort {

    Timer.Sample startSample();

    void incrementCounter(String metricName, Map<String, String> tags);

    void recordTimer(String metricName, Map<String, String> tags, Timer.Sample sample);

    void setGauge(String metricName, Map<String, String> tags, double value);

    void recordSuccess(String endpoint, String metricName, String application, Timer.Sample sample);

    void recordError(String endpoint, String metricName, String application, Timer.Sample sample);

    void recordMessageSend(String messageType, String application, boolean success);
}

