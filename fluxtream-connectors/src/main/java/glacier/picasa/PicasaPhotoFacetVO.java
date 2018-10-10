package glacier.picasa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.vos.AbstractPhotoFacetVO;
import com.fluxtream.domain.GuestSettings;
import com.fluxtream.mvc.models.DimensionModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class PicasaPhotoFacetVO extends AbstractPhotoFacetVO<PicasaPhotoFacet> {

	public String thumbnailUrl;

	private String thumbnailsJson;
	private JSONArray thumbnails;

	@Override
	public void fromFacet(PicasaPhotoFacet facet, TimeInterval timeInterval,
			GuestSettings settings) {
        start = facet.start;
		startMinute = toMinuteOfDay(new Date(facet.start), timeInterval.getMainTimeZone());
		thumbnailUrl = facet.thumbnailUrl;
		photoUrl = facet.photoUrl;
		description = facet.title;
		thumbnailsJson = facet.thumbnailsJson;
	}

    @Override
    public String getPhotoUrl() {
        return photoUrl;
    }

    @Override
    public String getThumbnail(int index) {
		if (thumbnails == null)
			thumbnails = JSONArray.fromObject(thumbnailsJson);
		if (index > thumbnails.size())
			return null;
		List<JSONObject> sortedThumbnails = getSortedThumbnails();
		return sortedThumbnails.get(index).getString("url");
	}

	@Override
	public List<DimensionModel> getThumbnailSizes() {
		Collection<JSONObject> sortedThumbnails = getSortedThumbnails();
		List<DimensionModel> dimensions = new ArrayList<DimensionModel>();
		Iterator<JSONObject> eachThumbnail = sortedThumbnails.iterator();
		while (eachThumbnail.hasNext()) {
			JSONObject jsonThumbnail = eachThumbnail.next();
			dimensions.add(new DimensionModel(jsonThumbnail.getInt("width"),
					jsonThumbnail.getInt("height")));
		}
		return dimensions;
	}

	private List<JSONObject> getSortedThumbnails() {
		if (thumbnails == null)
			thumbnails = JSONArray.fromObject(thumbnailsJson);
		List<JSONObject> toSort = new ArrayList<JSONObject>();
		for (int i=0; i<thumbnails.size(); i++)
			toSort.add(thumbnails.getJSONObject(i));
		
		Comparator<JSONObject> comparator = new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject t1, JSONObject t2) {
				return t1.getInt("width") - t2.getInt("width");
			}

		};
		Collections.sort(toSort, comparator);
		return toSort;
	}

}
