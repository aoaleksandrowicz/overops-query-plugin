package com.takipi.common.udf.regression;

import com.takipi.common.api.ApiClient;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;
import com.takipi.common.udf.util.ApiViewUtil;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RegressionUtils {

    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance();
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getNumberInstance();

    static {
        PERCENT_FORMAT.setMaximumFractionDigits(2);
    }

    public static class RegressionPair {
        private final EventResult baseEvent;
        private final EventResult activeEvent;

        RegressionPair(EventResult baseEvent, EventResult activeEvent) {
            this.baseEvent = baseEvent;
            this.activeEvent = activeEvent;
        }

        public EventResult getBaseEvent() {
            return baseEvent;
        }

        public EventResult getActiveEvent() {
            return activeEvent;
        }
    }

    public static class RateRegression {
        private final Map<String, EventResult> allNewEvents;

        private final Map<String, RegressionPair> allRegressions;
        private final Map<String, RegressionPair> criticalRegressions;

        private final Map<String, EventResult> exceededNewEvents;
        private final Map<String, EventResult> criticalNewEvents;

        private final Map<String, EventResult> baselineEvents;

        RateRegression() {
            allRegressions = new HashMap<>();
            criticalNewEvents = new HashMap<>();
            exceededNewEvents = new HashMap<>();
            allNewEvents = new HashMap<>();
            criticalRegressions = new HashMap<>();
            baselineEvents = new HashMap<>();
        }

        public Map<String, EventResult> getAllNewEvents() {
            return allNewEvents;
        }

        public Map<String, RegressionPair> getAllRegressions() {
            return allRegressions;
        }

        public Map<String, RegressionPair> getCriticalRegressions() {
            return criticalRegressions;
        }

        public Map<String, EventResult> getExceededNewEvents() {
            return exceededNewEvents;
        }

        public Map<String, EventResult> getCriticalNewEvents() {
            return criticalNewEvents;
        }

        public Map<String, EventResult> getBaselineEvents() {
            return baselineEvents;
        }
    }

    public static RateRegression calculateRateRegressions(ApiClient apiClient, String serviceId, String viewId,
                                                          int activeTimespan, int baselineTimespan, int minVolumeThreshold, double minErrorRateThreshold,
                                                          double regressionDelta, double criticalRegressionDelta, Collection<String> criticalExceptionTypes,
                                                          PrintStream printStream) {


        RateRegression result = new RateRegression();

        DateTime now = DateTime.now();
        DateTime activeFrom = now.minusMinutes(activeTimespan);

        EventsVolumeResult activeEventVolume = ApiViewUtil.getEventsVolume(apiClient, serviceId, viewId, activeFrom,
                now);

        DateTime baselineFrom = now.minusMinutes(baselineTimespan);

        EventsVolumeResult baselineEventVolume = ApiViewUtil.getEventsVolume(apiClient, serviceId, viewId, baselineFrom,
                now);

        for (EventResult eventResult : baselineEventVolume.events) {
            if (eventResult.stats == null) {
                continue;
            }

            result.getBaselineEvents().put(eventResult.id, eventResult);
        }

        for (EventResult activeEvent : activeEventVolume.events) {
            DateTime firstSeen = ISODateTimeFormat.dateTimeParser().parseDateTime(activeEvent.first_seen);

            boolean isNew = firstSeen.isAfter(activeFrom);

            if (isNew) {
                result.getAllNewEvents().put(activeEvent.id, activeEvent);

                // events types in the critical list are considered as new regardless of
                // threshold

                boolean isUncaught = activeEvent.type.equals("Uncaught Exception");
                boolean isCriticalEventType = criticalExceptionTypes.contains(activeEvent.name);

                if ((isUncaught) || (isCriticalEventType)) {
                    result.getCriticalNewEvents().put(activeEvent.id, activeEvent);

                    if (printStream != null) {
                        printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " - "
                                + activeEvent.name + " is critical with " + COUNT_FORMAT.format(activeEvent.stats.hits));
                    }

                    continue;
                }
            }

            if ((activeEvent.stats == null) || (activeEvent.stats.invocations == 0) || (activeEvent.stats.hits == 0)) {
                continue;
            }

            double activeEventRatio = ((double) activeEvent.stats.hits / (double) activeEvent.stats.invocations);

            if ((activeEventRatio < minErrorRateThreshold) || (activeEvent.stats.hits < minVolumeThreshold)) {
                continue;
            }

            if (isNew) {

                result.getExceededNewEvents().put(activeEvent.id, activeEvent);

                if (printStream != null) {
                    printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " is new with ER: "
                            + PERCENT_FORMAT.format(activeEventRatio) + " hits: " + COUNT_FORMAT.format(activeEvent.stats.hits));
                }

                continue;
            }

            if (regressionDelta == 0) {
                continue;
            }

            EventResult baseLineEvent = result.getBaselineEvents().get(activeEvent.id);

            if (baseLineEvent == null) {
                continue;
            }

            // see what the error rate is for the event
            double baselineEventRatio;

            if (baseLineEvent.stats.invocations > 0) {
                baselineEventRatio = (double) baseLineEvent.stats.hits / (double) baseLineEvent.stats.invocations;
            } else {
                baselineEventRatio = 0;
            }

            boolean regression;

            if (baselineEventRatio == 0) {
                regression = true;
            } else {
                // see if the error rate has increased by more than X%, if so an above min
                // volume, mark as regression
                regression = activeEventRatio - baselineEventRatio >= regressionDelta;
            }

            // check if this event can be considered a rate regression
            if (!regression) {
                continue;
            }
            result.getAllRegressions().put(activeEvent.id, new RegressionPair(baseLineEvent, activeEvent));

            if (printStream != null) {
                printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " regressed from ER: "
                        + PERCENT_FORMAT.format(baselineEventRatio) + " to: " + PERCENT_FORMAT.format(activeEventRatio) + " hits: " + COUNT_FORMAT.format(activeEvent.stats.hits));
            }

            // check if this event can be considered a critical rate regression
            if (criticalRegressionDelta == 0) {
                continue;
            }

            boolean criticalRegression = activeEventRatio - baselineEventRatio >= criticalRegressionDelta;

            if (!criticalRegression) {
                continue;
            }

            result.getCriticalRegressions().put(activeEvent.id, new RegressionPair(baseLineEvent, activeEvent));

            if (printStream != null) {
                printStream
                        .println("Event " + activeEvent.id + " " + activeEvent.type + " critically regressed from ER: "
                                + PERCENT_FORMAT.format(baselineEventRatio) + " to: " + PERCENT_FORMAT.format(activeEventRatio) + " hits: " + COUNT_FORMAT.format(activeEvent.stats.hits));
            }

        }

        return result;
    }
}
