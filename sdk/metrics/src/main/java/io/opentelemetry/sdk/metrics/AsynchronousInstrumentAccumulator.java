/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.sdk.metrics.aggregation.DoubleAccumulation;
import io.opentelemetry.sdk.metrics.aggregation.LongAccumulation;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.annotation.Nullable;

final class AsynchronousInstrumentAccumulator {
  private final ReentrantLock collectLock = new ReentrantLock();
  private final InstrumentProcessor instrumentProcessor;
  private final Runnable metricUpdater;

  static AsynchronousInstrumentAccumulator doubleAsynchronousAccumulator(
      InstrumentProcessor instrumentProcessor,
      @Nullable Consumer<AsynchronousInstrument.DoubleResult> metricUpdater) {
    // TODO: Decide what to do with null updater.
    if (metricUpdater == null) {
      return new AsynchronousInstrumentAccumulator(instrumentProcessor, () -> {});
    }
    AsynchronousInstrument.DoubleResult result =
        (value, labels) -> instrumentProcessor.batch(labels, DoubleAccumulation.create(value));

    return new AsynchronousInstrumentAccumulator(
        instrumentProcessor, () -> metricUpdater.accept(result));
  }

  static AsynchronousInstrumentAccumulator longAsynchronousAccumulator(
      InstrumentProcessor instrumentProcessor,
      @Nullable Consumer<AsynchronousInstrument.LongResult> metricUpdater) {
    // TODO: Decide what to do with null updater.
    if (metricUpdater == null) {
      return new AsynchronousInstrumentAccumulator(instrumentProcessor, () -> {});
    }

    AsynchronousInstrument.LongResult result =
        (value, labels) -> instrumentProcessor.batch(labels, LongAccumulation.create(value));

    return new AsynchronousInstrumentAccumulator(
        instrumentProcessor, () -> metricUpdater.accept(result));
  }

  private AsynchronousInstrumentAccumulator(
      InstrumentProcessor instrumentProcessor, Runnable metricUpdater) {
    this.metricUpdater = metricUpdater;
    this.instrumentProcessor = instrumentProcessor;
  }

  public List<MetricData> collectAll() {
    collectLock.lock();
    try {
      metricUpdater.run();
      return instrumentProcessor.completeCollectionCycle();
    } finally {
      collectLock.unlock();
    }
  }
}
