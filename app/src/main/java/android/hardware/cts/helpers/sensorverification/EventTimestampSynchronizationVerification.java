/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.SensorEvent;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} which verifies that the timestamp of the {@link SensorEvent} is
 * synchronized with {@link SystemClock#elapsedRealtimeNanos()}, based on a given threshold.
 */
public class EventTimestampSynchronizationVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "timestamp_synchronization_passed";

    // number of indices to print in assertion message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    private static final long DEFAULT_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(500);

    private final ArrayList<TestSensorEvent> mCollectedEvents = new ArrayList<TestSensorEvent>();

    private final long mMaximumSynchronizationErrorNs;
    private final long mReportLatencyNs;

    /**
     * Constructs an instance of {@link EventTimestampSynchronizationVerification}.
     *
     * @param maximumSynchronizationErrorNs The valid threshold for timestamp synchronization.
     * @param reportLatencyNs The latency on which batching events are received
     */
    public EventTimestampSynchronizationVerification(
            long maximumSynchronizationErrorNs,
            long reportLatencyNs) {
        mMaximumSynchronizationErrorNs = maximumSynchronizationErrorNs;
        mReportLatencyNs = reportLatencyNs;
    }

    /**
     * Gets a default {@link EventTimestampSynchronizationVerification}.
     *
     * @param environment The test environment
     * @return The verification or null if the verification is not supported in the given
     *         environment.
     */
    public static EventTimestampSynchronizationVerification getDefault(
            TestSensorEnvironment environment) {
        int reportLatencyUs = environment.getMaxReportLatencyUs();
        int fifoMaxEventCount = environment.getSensor().getFifoMaxEventCount();
        if (fifoMaxEventCount > 0) {
            int fifoBasedReportLatencyUs =
                    fifoMaxEventCount * environment.getMaximumExpectedSamplingPeriodUs();
            reportLatencyUs = Math.min(reportLatencyUs, fifoBasedReportLatencyUs);

        }
        long reportLatencyNs = TimeUnit.MICROSECONDS.toNanos(reportLatencyUs);
        return new EventTimestampSynchronizationVerification(DEFAULT_THRESHOLD_NS, reportLatencyNs);
    }

    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        StringBuilder errorMessageBuilder =
                new StringBuilder(" event timestamp synchronization failures: ");
        List<IndexedEvent> failures = verifyTimestampSynchronization(errorMessageBuilder);

        int failuresCount = failures.size();
        stats.addValue(SensorStats.EVENT_TIME_SYNCHRONIZATION_COUNT_KEY, failuresCount);
        stats.addValue(
                SensorStats.EVENT_TIME_SYNCHRONIZATION_POSITIONS_KEY,
                getIndexArray(failures));

        boolean success = failures.isEmpty();
        stats.addValue(PASSED_KEY, success);
        errorMessageBuilder.insert(0, failuresCount);
        Assert.assertTrue(errorMessageBuilder.toString(), success);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventTimestampSynchronizationVerification clone() {
        return new EventTimestampSynchronizationVerification(
                mMaximumSynchronizationErrorNs,
                mReportLatencyNs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        mCollectedEvents.add(event);
    }

    /**
     * Verifies timestamp synchronization for all sensor events.
     * The verification accounts for a lower and upper threshold, such thresholds are adjusted for
     * batching cases.
     *
     * @param builder A string builder to store error messaged found in the collected sensor events.
     * @return A list of events tha failed the verification.
     */
    private List<IndexedEvent> verifyTimestampSynchronization(StringBuilder builder) {
        int collectedEventsCount = mCollectedEvents.size();
        ArrayList<IndexedEvent> failures = new ArrayList<IndexedEvent>();

        for (int i = 0; i < collectedEventsCount; ++i) {
            TestSensorEvent event = mCollectedEvents.get(i);
            long eventTimestampNs = event.timestamp;
            long receivedTimestampNs = event.receivedTimestamp;
            long upperThresholdNs = receivedTimestampNs;
            long lowerThresholdNs = receivedTimestampNs - mMaximumSynchronizationErrorNs
                    - mReportLatencyNs;

            if (eventTimestampNs < lowerThresholdNs || eventTimestampNs > upperThresholdNs) {
                if (failures.size() < TRUNCATE_MESSAGE_LENGTH) {
                    builder.append("position=").append(i);
                    builder.append(", timestamp=").append(eventTimestampNs).append("ns");
                    builder.append(", expected=[").append(lowerThresholdNs);
                    builder.append(", ").append(upperThresholdNs).append("]ns; ");
                }
                failures.add(new IndexedEvent(i, event));
            }
        }
        if (failures.size() >= TRUNCATE_MESSAGE_LENGTH) {
            builder.append("more; ");
        }
        return failures;
    }
}
