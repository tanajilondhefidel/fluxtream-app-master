package com.fluxtream.connectors.mymee;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.services.impl.BodyTrackHelper;
import com.fluxtream.services.impl.FieldHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Candide Kemmler (candide@fluxtream.com)
 */
@Component("mymeeObservation")
public class MymeeObservationFieldHandler implements FieldHandler {

    @Autowired
    BodyTrackHelper bodyTrackHelper;

    // Marshall facet to datastore, using getChannelName as the channel name
    //
    // Standard marshalling of facets to datastore assumes fixed channel name, which
    // doesn't work for Mymee.
    @Override
    public void handleField(final long guestId, AbstractFacet facet) {
        MymeeObservationFacet observationFacet = (MymeeObservationFacet)facet;
        List<List<Object>> data = new ArrayList<List<Object>>();
        List<Object> row = new ArrayList<Object>();
        row.add(observationFacet.start / 1000.0);
        row.add(observationFacet.amount == null ? 0 : observationFacet.amount);
        row.add(observationFacet.note);
        data.add(row);
        String observationName = observationFacet.getChannelName();

        // TODO: check the status code in the BodyTrackUploadResult
        bodyTrackHelper.uploadToBodyTrack(guestId, "Mymee", Arrays.asList(observationName, observationName + "._comment"), data);
    }
}
