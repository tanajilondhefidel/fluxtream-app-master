package com.fluxtream.connectors.vos;

import java.util.List;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.images.ImageOrientation;
import com.fluxtream.mvc.models.DimensionModel;

public abstract class AbstractPhotoFacetVO<T extends AbstractFacet> extends
		AbstractInstantFacetVO<T> {

	public String photoUrl;
    // timeType is either "gmt" for facets that know their absolute time, or
    // "local" for facets that only know local time.  This defaults to "gmt"
    // and should be changed by subclasses such as Flickr which use local time.
    public String timeType="gmt";
	
	public abstract String getPhotoUrl();
	public abstract String getThumbnail(int index);
	public abstract List<DimensionModel> getThumbnailSizes();


    /**
     * Return the {@link ImageOrientation image's orientation}.  Defaults to {@link ImageOrientation#ORIENTATION_1} if
     * not overridden.
     */
    public ImageOrientation getOrientation() {
        return ImageOrientation.ORIENTATION_1;
    }

}
