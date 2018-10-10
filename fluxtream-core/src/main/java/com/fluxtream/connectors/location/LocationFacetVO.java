package com.fluxtream.connectors.location;

import java.util.Date;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractInstantFacetVO;
import com.fluxtream.domain.GuestSettings;

public class LocationFacetVO extends AbstractInstantFacetVO<LocationFacet> {

	private static DateTimeFormatter hmDateFormat = DateTimeFormat.forPattern("HH:mm");
	
	public float[] position;
	public String time;
    public int accuracy;
    public String source;
    public String uri;

	@Override
	public void fromFacet(
			LocationFacet facet, TimeInterval timeInterval, GuestSettings settings) {
		float[] position = new float[2];
		position[0] = facet.latitude;
		position[1] = facet.longitude;
		this.position = position;
        this.accuracy = facet.accuracy;
		Date date = new Date(facet.timestampMs);
		this.startMinute = toMinuteOfDay(date, timeInterval.getMainTimeZone());
		this.time = hmDateFormat.withZone(DateTimeZone.forTimeZone(timeInterval.getMainTimeZone())).print(date.getTime());
        source = facet.source.name();
        this.uri = facet.uri;
	}
	
}
