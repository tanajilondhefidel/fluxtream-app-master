package com.fluxtream.connectors.runkeeper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.services.impl.BodyTrackHelper;
import com.fluxtream.services.impl.FieldHandler;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * User: candide
 * Date: 21/10/13
 * Time: 00:28
 */
@Component("runkeeperPace")
public class RunkeeperPaceFieldHandler implements FieldHandler {

    @Autowired
    BodyTrackHelper bodyTrackHelper;

    public final static double MAX_MINUTES_PER_KM = 20.0;

    @Override
    public void handleField ( final long guestId, AbstractFacet facet) {
        RunKeeperFitnessActivityFacet activityFacet = (RunKeeperFitnessActivityFacet) facet;
        if (activityFacet.distanceStorage == null) {
            return;
        }
        JSONArray distanceJson = JSONArray.fromObject(activityFacet.distanceStorage);
        List<List<Object>> data = new ArrayList<List<Object>>();
        double lastTimestamp = 0d;
        double lastDistance = 0d;
        for(int i=0; i<distanceJson.size(); i++) {
            JSONObject record = distanceJson.getJSONObject(i);
            final double totalDistance = record.getInt("distance");
            final double timestamp = record.getInt("timestamp");
            final double lap = timestamp - lastTimestamp;
            final double distance = totalDistance - lastDistance;
            lastTimestamp = timestamp;
            lastDistance = totalDistance;

            // Ignore datapoints where either the time delta or
            // the distance is 0
            if (distance==0||lap==0)
                continue;
            final double minutesPerKilometer = ((1000d/distance)*lap)/60d;

            // Also ignore datapoints where the minutesPerKilometer
            // is over a threshold.  This happens when you've been stopped
            // for a while during the time interval. If we include
            // those datapoints then they dominate and can cause the
            // real data we care about to be too small to see.
            // We chose the limit of 20 min/km based on wikipedia's
            // claims about human walking pace and on comparing
            // unfiltered graphs against the graphs that runkeeper
            // generates.
            if(minutesPerKilometer > MAX_MINUTES_PER_KM)
                continue;

            // Set the time to be the center of the interval
            // between the earlier and later of the readings
            // used to generate this datapoint
            double when = (((double)facet.start)/1000.0d) + timestamp - lap/2.0d;

            List<Object> siRecord = new ArrayList<Object>();
            siRecord.add(when);
            siRecord.add(minutesPerKilometer);
            siRecord.add(minutesPerKilometer/.621371192d);
            data.add(siRecord);
        }
        final List<String> channelNames = Arrays.asList("minutesPerKilometer", "minutesPerMile");

        // TODO: check the status code in the BodyTrackUploadResult
        bodyTrackHelper.uploadToBodyTrack(guestId, "runkeeper", channelNames, data);
    }

}
