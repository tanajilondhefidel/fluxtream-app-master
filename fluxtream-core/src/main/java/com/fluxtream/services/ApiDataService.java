package com.fluxtream.services;

import java.util.List;
import com.fluxtream.TimeInterval;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.connectors.location.LocationFacet;
import com.fluxtream.connectors.updaters.UpdateInfo;
import com.fluxtream.connectors.vos.AbstractFacetVO;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.domain.AbstractRepeatableFacet;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.TagFilter;
import net.sf.json.JSONObject;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

public interface ApiDataService {

    public AbstractFacetVO<AbstractFacet> getFacet(int api, int objectType, long facetId);

	public void cacheApiDataObject(UpdateInfo updateInfo, long start, long end,
			AbstractFacet payload) throws Exception;

	public void cacheApiDataJSON(UpdateInfo updateInfo, JSONObject jsonObject,
			long start, long end) throws Exception;

    public void cacheApiDataJSON(UpdateInfo updateInfo, String json,
                                 long start, long end, int objectTypes) throws Exception;

	public void cacheApiDataJSON(UpdateInfo updateInfo, String json,
			long start, long end) throws Exception;

	public void cacheApiDataXML(UpdateInfo updateInfo, String xml, long start,
			long end) throws Exception;

    public List<AbstractFacet> getApiDataFacets(ApiKey apiKey,
                                                ObjectType objectType, List<String> dates);

    public List<AbstractFacet> getApiDataFacets(ApiKey apiKey, ObjectType objectType, TimeInterval timeInterval);

    public List<AbstractFacet> getApiDataFacets(ApiKey apiKey,
                                                ObjectType objectType,
                                                TimeInterval timeInterval,
                                                @Nullable TagFilter tagFilter);

    public AbstractFacet getOldestApiDataFacet(ApiKey apiKey, ObjectType objectType);
    public AbstractFacet getLatestApiDataFacet(ApiKey apiKey, ObjectType objectType);

    /**
     * Returns up to <code>desiredCount</code> facets which have a timestamp equal to or before the given
     * <code>timeInMillis</code>.  Returns <code>null</code> if no facets are found.
     */
    public List<AbstractFacet> getApiDataFacetsBefore(ApiKey apiKey,
                                                      ObjectType objectType,
                                                      long timeInMillis,
                                                      int desiredCount);

    /**
     * Returns up to <code>desiredCount</code> facets which have a timestamp equal to or before the given
     * <code>timeInMillis</code>.  Returns <code>null</code> if no facets are found.
     */
    public List<AbstractFacet> getApiDataFacetsBefore(ApiKey apiKey,
                                                      ObjectType objectType,
                                                      long timeInMillis,
                                                      int desiredCount,
                                                      @Nullable TagFilter tagFilter);

    /**
     * Returns up to <code>desiredCount</code> facets which have a timestamp equal to or after the given
     * <code>timeInMillis</code>.  Returns <code>null</code> if no facets are found.
     */
    public List<AbstractFacet> getApiDataFacetsAfter(ApiKey apiKey,
                                                     ObjectType objectType,
                                                     long timeInMillis,
                                                     int desiredCount);

    /**
     * Returns up to <code>desiredCount</code> facets which have a timestamp equal to or after the given
     * <code>timeInMillis</code>.  Returns <code>null</code> if no facets are found.
     */
    public List<AbstractFacet> getApiDataFacetsAfter(ApiKey apiKey,
                                                     ObjectType objectType,
                                                     long timeInMillis,
                                                     int desiredCount,
                                                     @Nullable TagFilter tagFilter);

    public AbstractFacet getFacetById(ApiKey apiKey, ObjectType objectType, long facetId);

    public AbstractFacet persistFacet(AbstractFacet facet);

    public void persistExistingFacet(final AbstractFacet facet);

    // addGuestLocation(s) persists the location or list of locations and adds them to the visited cities
    // table.  The persistence does duplicate detection by checking for locations matching the time, source,
    // and apiKeyId.  In the case of a duplicate the new locationFacet is not persisted.
    void addGuestLocation(long guestId, LocationFacet locationResource);
    void addGuestLocations(long guestId, List<LocationFacet> locationResources);

    void deleteStaleData() throws ClassNotFoundException;

    @Transactional(readOnly = false)
    void cleanupStaleData() throws ClassNotFoundException, Exception;

    @Transactional(readOnly=false)
    void setComment(String connectorName, String objectTypeName, long guestId, long facetId, String comment);

    public List<AbstractRepeatableFacet> getApiDataFacets(ApiKey apiKey, ObjectType objectType, String startDate, String endDate);

    // Pass this to createOrReadModifyWrite
    public interface FacetModifier<T extends AbstractFacet> {
        // Override this with your code to either modify or create
        // a facet.  If you are passed facet != null, modify that
        // facet and return it.  If you are passed facet == null,
        // create a new facet, fill it in, and return it
        public T createOrModify(T facet, Long apiKeyId);
    }

    public class FacetQuery {
        public String query;
        public Object[] args;
        // query must include reference to "e", and question marks for each arg, e.g.
        // new FacetQuery("e.guestId = ? AND e.mymeeId = ?", guestId, mymeeId);
        public FacetQuery(String query, Object... args) {
            this.query = query;
            this.args = args;
        }
    }

    public <T extends AbstractFacet> T createOrReadModifyWrite(Class<? extends AbstractFacet> facetClass, FacetQuery query, FacetModifier<T> modifier, Long apiKeyId);

	public void eraseApiData(ApiKey apiKey);

	public void eraseApiData(ApiKey apiKey, int objectTypes);

	public void eraseApiData(ApiKey apiKey, ObjectType objectType);

	public void eraseApiData(ApiKey apiKey, int objectTypes,
			TimeInterval timeInterval);

	public void eraseApiData(ApiKey apiKey,
			ObjectType objectType, TimeInterval timeInterval);

    public void eraseApiData(ApiKey apiKey,
                             ObjectType objectType, List<String> dates);

	public void cacheEmptyData(UpdateInfo updateInfo, long fromMidnight,
			long toMidnight);


    void deleteComment(String connectorName, String objectTypeName, long guestId, long facetId);

}
