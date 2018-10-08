package org.overops.plugins.jenkins.query;

import java.text.NumberFormat;
import java.util.regex.Pattern;

import com.takipi.common.api.result.event.EventResult;

public class OOReportEvent {
	static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance();
	static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();

	static {
		PERCENT_FORMAT.setMaximumFractionDigits(2);
		PERCENT_FORMAT.setMinimumFractionDigits(2);
		PERCENT_FORMAT.setMinimumIntegerDigits(1);
	}

	static final String NEW_ISSUE = "New";
	static final String SEVERE_NEW = "Severe New";
	static final String REGRESSION = "Regression";
	static final String SEVERE_REGRESSION = "Severe Regression";
	
	protected final EventResult event;
	protected final String arcLink;
	protected final String type;

	public OOReportEvent(EventResult event, String type, String arcLink) {
		this.event = event;
		this.arcLink = arcLink;
		this.type = type;
	}

	public String getEventSummary() {
		
		String[] parts = event.error_location.class_name.split(Pattern.quote("."));
		
		String simpleClassName;
		
		if (parts.length > 0) {
			simpleClassName = parts[parts.length - 1];
		} else {
			simpleClassName = event.error_location.class_name;
		}

		return event.type + " in " + simpleClassName + "." + event.error_location.method_name;
	}

	public String getEventRate() {
		StringBuilder result = new StringBuilder();

		result.append(NUMBER_FORMAT.format(event.stats.hits));

		if (event.stats.hits > 0 && event.stats.invocations > 0) {

			double rate = (double) event.stats.hits / (double) event.stats.invocations;
			result.append(" (");
			result.append(PERCENT_FORMAT.format(rate));
			result.append(")");

		}
		
		return result.toString();
	}

	public String getIntroducedBy() {
		return event.introduced_by;
	}

	public String getType() {
		return type;
	}

	public String getARCLink() {
		return arcLink;
	}

	public long getHits() {
		return event.stats.hits;
	}

	public long getCalls() {
		return event.stats.invocations;
	}
}
