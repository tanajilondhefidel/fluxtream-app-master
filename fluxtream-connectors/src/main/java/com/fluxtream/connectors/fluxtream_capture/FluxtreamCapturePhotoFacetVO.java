package com.fluxtream.connectors.fluxtream_capture;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import com.fluxtream.OutsideTimeBoundariesException;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractPhotoFacetVO;
import com.fluxtream.domain.GuestSettings;
import com.fluxtream.images.ImageOrientation;
import com.fluxtream.mvc.models.DimensionModel;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class FluxtreamCapturePhotoFacetVO extends AbstractPhotoFacetVO<FluxtreamCapturePhotoFacet> {

    public Map<Integer, String> thumbnailUrls = new HashMap<Integer, String>(FluxtreamCapturePhotoFacet.NUM_THUMBNAILS);
    public SortedMap<Integer, Dimension> thumbnailSizes = new TreeMap<Integer, Dimension>();

    public transient ImageOrientation imageOrientation;

    public int orientation;

    @Override
    protected void fromFacet(final FluxtreamCapturePhotoFacet facet, final TimeInterval timeInterval, final GuestSettings settings)
            throws OutsideTimeBoundariesException {
        start = facet.start;
        startMinute = toMinuteOfDay(new Date(facet.start), timeInterval.getTimeZone(facet.start));

        final String photoStoreKey = facet.getPhotoStoreKey();
        photoUrl = "/api/bodytrack/photo/" + photoStoreKey;

        // build the Map of thumbnail URLS and the SortedMap of thumbnail sizes
        for (int i = 0; i < FluxtreamCapturePhotoFacet.NUM_THUMBNAILS; i++) {
            thumbnailUrls.put(i, "/api/bodytrack/photoThumbnail/" + facet.getGuestId() + "/" + facet.getId() + "/" + i);
            thumbnailSizes.put(i, facet.getThumbnailSize(i));
        }
        imageOrientation = facet.getOrientation();
        orientation = imageOrientation.getId();
    }

    @Override
    public String getPhotoUrl() {
        return photoUrl;
    }

    @Override
    public String getThumbnail(final int index) {
        return thumbnailUrls.get(index);
    }

    @Override
    public List<DimensionModel> getThumbnailSizes() {
        List<DimensionModel> sizes = new ArrayList<DimensionModel>();
        for (final Dimension dimension : thumbnailSizes.values()) {
            sizes.add(new DimensionModel(dimension.width, dimension.height)); // create a copy so the caller can't modify this instance
        }
        return sizes;
    }

    @Override
    public ImageOrientation getOrientation() {
        return imageOrientation;
    }
}
