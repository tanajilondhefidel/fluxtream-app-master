package com.fluxtream.connectors.mymee;

import java.util.Date;
import java.util.List;
import com.fluxtream.OutsideTimeBoundariesException;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractPhotoFacetVO;
import com.fluxtream.domain.GuestSettings;
import com.fluxtream.mvc.models.DimensionModel;

/**
 *
 * @author Candide Kemmler (candide@fluxtream.com)
 */
public class MymeeObservationFacetVO extends AbstractPhotoFacetVO<MymeeObservationFacet> {

    public String mymeeId;
    public String name;
    public String note = "";
    public String user = "";
    public Integer timezoneOffset;
    public Double amount;
    public Integer baseAmount;
    public String unit;
    public String baseUnit;
    public float[] position;

    @Override
    protected void fromFacet(final MymeeObservationFacet facet, final TimeInterval timeInterval, final GuestSettings settings)
            throws OutsideTimeBoundariesException {
        startMinute = toMinuteOfDay(new Date(facet.start), timeInterval.getTimeZone(facet.start));
        this.start = facet.start;
        this.mymeeId = facet.mymeeId;
        this.name = facet.name;
        this.note = facet.note;
        this.user = facet.user;
        this.timezoneOffset = facet.timezoneOffset;
        this.amount = facet.amount;
        this.baseAmount = facet.baseAmount;
        this.unit = facet.unit;
        this.baseUnit = facet.baseUnit;
        this.photoUrl = facet.imageURL;
        if (facet.longitude != null){
            position = new float[2];
            position[0] = facet.latitude.floatValue();
            position[1] = facet.longitude.floatValue();
        }
    }

    double round(double v) {
        return (double) Math.round(v * 100) / 100;
    }

    @Override
    public String getPhotoUrl() {
        return this.photoUrl;
    }

    @Override
    public String getThumbnail(final int index) {
        // TODO: is a thumbnail version available?
        return this.photoUrl;
    }

    @Override
    public List<DimensionModel> getThumbnailSizes() {
        // TODO
        return null;
    }
}
