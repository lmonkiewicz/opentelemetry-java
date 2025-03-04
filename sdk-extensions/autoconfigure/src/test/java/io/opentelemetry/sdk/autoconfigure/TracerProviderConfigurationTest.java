/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.internal.JcTools;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// NB: We use AssertJ extracting to reflectively access implementation details to test configuration
// because the use of BatchSpanProcessor makes it difficult to verify values through public means.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracerProviderConfigurationTest {

  private static final ConfigProperties EMPTY =
      DefaultConfigProperties.createForTest(Collections.emptyMap());

  @Mock private SpanExporter mockSpanExporter;

  @BeforeEach
  void setUp() {
    when(mockSpanExporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
  }

  @Test
  void configureTracerProvider() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.bsp.schedule.delay", "100000");
    properties.put("otel.traces.sampler", "always_off");
    properties.put("otel.traces.exporter", "none");

    Resource resource = Resource.create(Attributes.builder().put("cat", "meow").build());
    // We don't have any exporters on classpath for this test so check no-op case. Exporter cases
    // are verified in other test sets like testFullConfig.
    SdkTracerProviderBuilder tracerProviderBuilder =
        SdkTracerProvider.builder().setResource(resource);
    TracerProviderConfiguration.configureTracerProvider(
        tracerProviderBuilder,
        DefaultConfigProperties.createForTest(properties),
        TracerProviderConfiguration.class.getClassLoader(),
        MeterProvider.noop(),
        (a, unused) -> a,
        (a, unused) -> a);
    SdkTracerProvider tracerProvider = tracerProviderBuilder.build();
    try {
      assertThat(tracerProvider.getSampler()).isEqualTo(Sampler.alwaysOff());

      assertThat(tracerProvider)
          .extracting("sharedState")
          .satisfies(
              sharedState -> {
                assertThat(sharedState).extracting("resource").isEqualTo(resource);
                assertThat(sharedState)
                    .extracting("activeSpanProcessor")
                    .isEqualTo(SpanProcessor.composite());
              });
    } finally {
      tracerProvider.shutdown();
    }
  }

  @Test
  void configureBatchSpanProcessor_empty() {
    BatchSpanProcessor processor =
        TracerProviderConfiguration.configureBatchSpanProcessor(
            EMPTY, mockSpanExporter, MeterProvider.noop());

    try {
      assertThat(processor)
          .extracting("worker")
          .satisfies(
              worker -> {
                assertThat(worker)
                    .extracting("scheduleDelayNanos")
                    .isEqualTo(TimeUnit.MILLISECONDS.toNanos(5000));
                assertThat(worker)
                    .extracting("exporterTimeoutNanos")
                    .isEqualTo(TimeUnit.MILLISECONDS.toNanos(30000));
                assertThat(worker).extracting("maxExportBatchSize").isEqualTo(512);
                assertThat(worker)
                    .extracting("queue")
                    .isInstanceOfSatisfying(
                        Queue.class, queue -> assertThat(JcTools.capacity(queue)).isEqualTo(2048));
                assertThat(worker).extracting("spanExporter").isEqualTo(mockSpanExporter);
              });
    } finally {
      processor.shutdown();
    }
  }

  @Test
  void configureBatchSpanProcessor_configured() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.bsp.schedule.delay", "100000");
    properties.put("otel.bsp.max.queue.size", "2");
    properties.put("otel.bsp.max.export.batch.size", "3");
    properties.put("otel.bsp.export.timeout", "4");

    BatchSpanProcessor processor =
        TracerProviderConfiguration.configureBatchSpanProcessor(
            DefaultConfigProperties.createForTest(properties),
            mockSpanExporter,
            MeterProvider.noop());

    try {
      assertThat(processor)
          .extracting("worker")
          .satisfies(
              worker -> {
                assertThat(worker)
                    .extracting("scheduleDelayNanos")
                    .isEqualTo(TimeUnit.MILLISECONDS.toNanos(100000));
                assertThat(worker)
                    .extracting("exporterTimeoutNanos")
                    .isEqualTo(TimeUnit.MILLISECONDS.toNanos(4));
                assertThat(worker).extracting("maxExportBatchSize").isEqualTo(3);
                assertThat(worker)
                    .extracting("queue")
                    .isInstanceOfSatisfying(
                        Queue.class, queue -> assertThat(JcTools.capacity(queue)).isEqualTo(2));
                assertThat(worker).extracting("spanExporter").isEqualTo(mockSpanExporter);
              });
    } finally {
      processor.shutdown();
    }
  }

  @Test
  void configureSpanLimits() {
    assertThat(TracerProviderConfiguration.configureSpanLimits(EMPTY))
        .isEqualTo(SpanLimits.getDefault());

    SpanLimits config =
        TracerProviderConfiguration.configureSpanLimits(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    "otel.attribute.value.length.limit", "100",
                    "otel.attribute.count.limit", "5")));
    assertThat(config.getMaxAttributeValueLength()).isEqualTo(100);
    assertThat(config.getMaxNumberOfAttributes()).isEqualTo(5);
    assertThat(config.getMaxNumberOfAttributesPerEvent()).isEqualTo(5);
    assertThat(config.getMaxNumberOfAttributesPerLink()).isEqualTo(5);
    assertThat(config.getMaxNumberOfEvents())
        .isEqualTo(SpanLimits.getDefault().getMaxNumberOfEvents());
    assertThat(config.getMaxNumberOfLinks())
        .isEqualTo(SpanLimits.getDefault().getMaxNumberOfLinks());

    config =
        TracerProviderConfiguration.configureSpanLimits(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    "otel.attribute.value.length.limit", "100",
                    "otel.span.attribute.value.length.limit", "200",
                    "otel.attribute.count.limit", "5",
                    "otel.span.attribute.count.limit", "10",
                    "otel.span.event.count.limit", "4",
                    "otel.span.link.count.limit", "3")));
    assertThat(config.getMaxAttributeValueLength()).isEqualTo(200);
    assertThat(config.getMaxNumberOfAttributes()).isEqualTo(10);
    assertThat(config.getMaxNumberOfAttributesPerEvent()).isEqualTo(5);
    assertThat(config.getMaxNumberOfAttributesPerLink()).isEqualTo(5);
    assertThat(config.getMaxNumberOfEvents()).isEqualTo(4);
    assertThat(config.getMaxNumberOfLinks()).isEqualTo(3);
  }

  @Test
  void configureSampler() {
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "always_on", EMPTY, TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.alwaysOn());
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "always_off", EMPTY, TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.alwaysOff());
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "traceidratio",
                DefaultConfigProperties.createForTest(
                    Collections.singletonMap("otel.traces.sampler.arg", "0.5")),
                TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.traceIdRatioBased(0.5));
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "traceidratio", EMPTY, TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.traceIdRatioBased(1.0d));
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "parentbased_always_on", EMPTY, TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.parentBased(Sampler.alwaysOn()));
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "parentbased_always_off",
                EMPTY,
                TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.parentBased(Sampler.alwaysOff()));
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "parentbased_traceidratio",
                DefaultConfigProperties.createForTest(
                    Collections.singletonMap("otel.traces.sampler.arg", "0.4")),
                TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.parentBased(Sampler.traceIdRatioBased(0.4)));
    assertThat(
            TracerProviderConfiguration.configureSampler(
                "parentbased_traceidratio",
                EMPTY,
                TracerProviderConfiguration.class.getClassLoader()))
        .isEqualTo(Sampler.parentBased(Sampler.traceIdRatioBased(1.0d)));

    assertThatThrownBy(
            () ->
                TracerProviderConfiguration.configureSampler(
                    "catsampler", EMPTY, TracerProviderConfiguration.class.getClassLoader()))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Unrecognized value for otel.traces.sampler: catsampler");
  }
}
