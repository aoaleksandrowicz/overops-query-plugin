package com.overops.plugins.jenkins.query;

import com.takipi.common.api.result.event.EventResult;

public class OOReportRegressedEvent extends OOReportEvent {

    private final EventResult baseLineEvent;

    public OOReportRegressedEvent(EventResult activeEvent, EventResult baseLineEvent, String type, String arcLink) {

        super(activeEvent, type, arcLink);

        this.baseLineEvent = baseLineEvent;
    }

    @Override
    public String getEventRate() {
        double rate = 0.0;

        if (baseLineEvent.stats.hits > 0 && baseLineEvent.stats.invocations > 0) {
            rate = (double) baseLineEvent.stats.hits / (double) baseLineEvent.stats.invocations;
        }

        return super.getEventRate() +
                " from " +
                PERCENT_FORMAT.format(rate);
    }

    public long getBaselineHits() {
        return baseLineEvent.stats.hits;
    }

    public long getBaselineCalls() {
        return baseLineEvent.stats.invocations;
    }
}
