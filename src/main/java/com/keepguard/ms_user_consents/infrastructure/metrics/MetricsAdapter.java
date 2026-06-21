package com.keepguard.ms_user_consents.infrastructure.metrics;

import com.keepguard.lib_common.metrics.service.MetricsService;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetricsAdapter implements MetricsPort {

    private final MetricsService metricsService;

    @Override
    public Timer.Sample startSample() {
        return metricsService.startSample();
    }

    @Override
    public void incrementCounter(String metricName, Map<String, String> tags) {
        metricsService.incrementCounter(metricName, tags);
    }

    @Override
    public void recordTimer(String metricName, Map<String, String> tags, Timer.Sample sample) {
        metricsService.recordTimer(metricName, tags, sample);
    }

    @Override
    public void setGauge(String metricName, Map<String, String> tags, double value) {
        metricsService.setGauge(metricName, tags, value);
    }

    @Override
    public void recordSuccess(String endpoint, String metricName, String application, Timer.Sample sample) {
        metricsService.recordSuccess(endpoint, metricName, application, sample);
    }

    @Override
    public void recordError(String endpoint, String metricName, String application, Timer.Sample sample) {
        metricsService.recordError(endpoint, metricName, application, sample);
    }

    @Override
    public void recordMessageSend(String messageType, String application, boolean success) {
        metricsService.recordMessageSend(messageType, application, success);
    }

}

