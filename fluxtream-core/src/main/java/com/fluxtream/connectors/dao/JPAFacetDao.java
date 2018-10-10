package com.fluxtream.connectors.dao;

import java.lang.reflect.Method;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import com.fluxtream.TimeInterval;
import com.fluxtream.aspects.FlxLogger;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.connectors.annotations.ObjectTypeSpec;
import com.fluxtream.connectors.location.LocationFacet;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.domain.AbstractRepeatableFacet;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.TagFilter;
import com.fluxtream.domain.metadata.VisitedCity;
import com.fluxtream.services.ConnectorUpdateService;
import com.fluxtream.services.GuestService;
import com.fluxtream.utils.JPAUtils;
import com.fluxtream.utils.TimeUtils;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Component
public class JPAFacetDao implements FacetDao {

    private static final FlxLogger logger = FlxLogger.getLogger(JPAFacetDao.class);

    @Autowired
	GuestService guestService;

    @Qualifier("connectorUpdateServiceImpl")
    @Autowired
	ConnectorUpdateService connectorUpdateService;

	@PersistenceContext
	private EntityManager em;

	public JPAFacetDao() {}

    @Override
    public List<AbstractFacet> getFacetsByDates(final ApiKey apiKey, ObjectType objectType, List<String> dates) {
        ArrayList<AbstractFacet> facets = new ArrayList<AbstractFacet>();
        if (!apiKey.getConnector().hasFacets()) return facets;
        Class<? extends AbstractFacet> facetClass = getFacetClass(apiKey.getConnector(), objectType);
        final String facetName = getEntityName(facetClass);
        String queryString = "SELECT facet FROM " + facetName + " facet WHERE facet.apiKeyId=:apiKeyId AND facet.date IN :dates";
        final TypedQuery<? extends AbstractFacet> query = em.createQuery(queryString, AbstractFacet.class);
        query.setParameter("apiKeyId", apiKey.getId());
        query.setParameter("dates", dates);
        List<? extends AbstractFacet> found = query.getResultList();
        if (found!=null)
            facets.addAll(found);
        return facets;
    }

    @Override
    public List<AbstractRepeatableFacet> getFacetsBetweenDates(final ApiKey apiKey, final ObjectType objectType, final String startDateString, final String endDateString) {
        ArrayList<AbstractRepeatableFacet> facets = new ArrayList<AbstractRepeatableFacet>();
        if (!apiKey.getConnector().hasFacets()) return facets;
        Class<? extends AbstractRepeatableFacet> facetClass = (Class<? extends AbstractRepeatableFacet>)getFacetClass(apiKey.getConnector(), objectType);
        final String facetName = getEntityName(facetClass);
        String queryString = "SELECT facet FROM " + facetName + " facet WHERE facet.apiKeyId=:apiKeyId AND NOT(facet.endDate<:startDate) AND NOT(facet.startDate>:endDate)";
        final TypedQuery<? extends AbstractFacet> query = em.createQuery(queryString, AbstractFacet.class);
        query.setParameter("apiKeyId", apiKey.getId());
        final DateTime time = TimeUtils.dateFormatterUTC.parseDateTime(startDateString);
        Date startDate = new Date(time.getMillis());
        final DateTime time2 = TimeUtils.dateFormatterUTC.parseDateTime(endDateString);
        Date endDate = new Date(time2.getMillis());
        query.setParameter("startDate", startDate, TemporalType.DATE);
        query.setParameter("endDate", endDate, TemporalType.DATE);
        List<? extends AbstractRepeatableFacet> found = (List<? extends AbstractRepeatableFacet>)query.getResultList();
        if (found!=null)
            facets.addAll(found);
        return facets;
    }

    @Cacheable("facetClasses")
    private String getEntityName(Class<? extends AbstractFacet> facetClass) {
        try {
            return facetClass.getAnnotation(Entity.class).name();
        } catch (Throwable t) {
            final String message = "Could not get Facet class for connector for " + facetClass.getName();
            logger.error(message);
            throw new RuntimeException(message);
        }
    }

    private Class<? extends AbstractFacet> getFacetClass(final Connector connector, final ObjectType objectType) {
        return objectType!=null
                        ? objectType.facetClass()
                        : connector.facetClass();
    }

	@Override
	public List<AbstractFacet> getFacetsBetween(final ApiKey apiKey, ObjectType objectType, TimeInterval timeInterval) {
        return getFacetsBetween(apiKey, objectType, timeInterval, null);
    }

    @Override
    public List<AbstractFacet> getFacetsBetween(final ApiKey apiKey,
                                                final ObjectType objectType,
                                                final TimeInterval timeInterval,
                                                @Nullable final TagFilter tagFilter) {
        if (objectType==null) {
            return getFacetsBetween(apiKey, timeInterval, tagFilter);
        } else {
            if (!apiKey.getConnector().hasFacets()) return new ArrayList<AbstractFacet>();
            Class<? extends AbstractFacet> facetClass = getFacetClass(apiKey.getConnector(), objectType);
            final String facetName = getEntityName(facetClass);
            String additionalWhereClause = (tagFilter == null) ? "" : " AND (" + tagFilter.getWhereClause() + ")";
            if (objectType.isMixedType()) additionalWhereClause += " AND facet.allDayEvent=false ";
            String queryString = "SELECT facet FROM " + facetName  + " facet WHERE facet.apiKeyId=? AND facet.end>=? AND facet.start<=?" + additionalWhereClause;
            final TypedQuery<AbstractFacet> query = em.createQuery(queryString, AbstractFacet.class);
            query.setParameter(1, apiKey.getId());
            query.setParameter(2, timeInterval.getStart());
            query.setParameter(3, timeInterval.getEnd());
            List<AbstractFacet> facets = query.getResultList();
            return facets;
        }
    }

    private List<AbstractFacet> getFacetsBetween(final ApiKey apiKey, TimeInterval timeInterval, @Nullable final TagFilter tagFilter) {
        final ObjectType[] objectTypes = apiKey.getConnector().objectTypes();
        List<AbstractFacet> facets = new ArrayList<AbstractFacet>();
        for (ObjectType type : objectTypes) {
            facets.addAll(getFacetsBetween(apiKey, type, timeInterval, tagFilter));
        }
        return facets;
    }

    @Override
    public AbstractFacet getOldestFacet(final ApiKey apiKey, final ObjectType objectType) {
        return getFacet(apiKey, objectType, "getOldestFacet");
    }

    @Override
    public AbstractFacet getLatestFacet(final ApiKey apiKey, final ObjectType objectType) {
        return getFacet(apiKey, objectType, "getLatestFacet");
    }

    @Override
    public List<AbstractFacet> getFacetsBefore(final ApiKey apiKey, final ObjectType objectType, final long timeInMillis, final int desiredCount) {
        return getFacetsBefore(apiKey, objectType, timeInMillis, desiredCount, null);
    }

    @Override
    public List<AbstractFacet> getFacetsAfter(final ApiKey apiKey, final ObjectType objectType, final long timeInMillis, final int desiredCount) {
        return getFacetsAfter(apiKey, objectType, timeInMillis, desiredCount, null);
    }

    @Override
    public List<AbstractFacet> getFacetsBefore(final ApiKey apiKey,
                                               final ObjectType objectType,
                                               final long timeInMillis,
                                               final int desiredCount,
                                               @Nullable final TagFilter tagFilter) {
        return getFacets(apiKey, objectType, timeInMillis, desiredCount, "getFacetsBefore", tagFilter);
    }

    @Override
    public List<AbstractFacet> getFacetsAfter(final ApiKey apiKey,
                                              final ObjectType objectType,
                                              final long timeInMillis,
                                              final int desiredCount,
                                              @Nullable final TagFilter tagFilter) {
        return getFacets(apiKey, objectType, timeInMillis, desiredCount, "getFacetsAfter", tagFilter);
    }

    @Override
    public AbstractFacet getFacetById(ApiKey apiKey, final ObjectType objectType, final long facetId) {
        final Class<? extends AbstractFacet> facetClass = objectType.facetClass();
        final Entity entity = facetClass.getAnnotation(Entity.class);
        final TypedQuery<? extends AbstractFacet> query = em.createQuery("SELECT facet FROM " + entity.name() + " facet WHERE facet.id = " + facetId + " AND facet.guestId = " + apiKey.getGuestId(), facetClass);
        query.setMaxResults(1);

        final List resultList = query.getResultList();
        if (resultList != null && resultList.size() > 0) {
            return (AbstractFacet)resultList.get(0);
        }
        return null;
    }

    private AbstractFacet getFacet(final ApiKey apiKey, final ObjectType objectType, final String methodName) {
        if (!apiKey.getConnector().hasFacets()) {
            return null;
        }

        AbstractFacet facet = null;
        if (objectType != null) {
            try {
                Class c = objectType.facetClass();
                Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class);
                facet = (AbstractFacet)m.invoke(null, em, apiKey, objectType);
            }
            catch (Exception ignored) {
                if (logger.isInfoEnabled()) {
                    logger.info("JPAFacetDao.getFacet(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                }
            }
        }
        else {
            if (apiKey.getConnector().objectTypes() != null) {
                for (ObjectType type : apiKey.getConnector().objectTypes()) {
                    AbstractFacet fac = null;
                    try {
                        Class c = type.facetClass();
                        Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class);
                        fac = (AbstractFacet)m.invoke(null, em, apiKey, type);
                    }
                    catch (Exception ignored) {
                        if (logger.isInfoEnabled()) {
                            logger.info("JPAFacetDao.getFacet(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                        }
                    }
                    if (facet == null || (fac != null && fac.end > facet.end)) {
                        facet = fac;
                    }
                }
            }
            else {
                try {
                    Class c = apiKey.getConnector().facetClass();
                    Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class);
                    facet = (AbstractFacet)m.invoke(null, em, apiKey, null);
                }
                catch (Exception ignored) {
                    if (logger.isInfoEnabled()) {
                        logger.info("JPAFacetDao.getFacet(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                    }
                }
            }
        }
        return facet;
    }

    private List<AbstractFacet> getFacets(final ApiKey apiKey,
                                          final ObjectType objectType,
                                          final long timeInMillis,
                                          final int desiredCount,
                                          final String methodName,
                                          @Nullable final TagFilter tagFilter) {
        if (!apiKey.getConnector().hasFacets()) {
            return null;
        }

        List<AbstractFacet> facets = null;
        if (objectType != null) {
            try {
                Class c = objectType.facetClass();
                Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class, Long.class, Integer.class, TagFilter.class);
                facets = (List<AbstractFacet>)m.invoke(null, em, apiKey, objectType, timeInMillis, desiredCount, tagFilter);
            }
            catch (Exception ignored) {
                if (logger.isInfoEnabled()) {
                    logger.info("JPAFacetDao.getFacets(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                }
            }
        }
        else {
            if (apiKey.getConnector().objectTypes() != null) {
                for (ObjectType type : apiKey.getConnector().objectTypes()) {
                    try {
                        Class c = type.facetClass();
                        Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class, Long.class, Integer.class, TagFilter.class);
                        facets = (List<AbstractFacet>)m.invoke(null, em, apiKey, type, timeInMillis, desiredCount, tagFilter);
                    }
                    catch (Exception ignored) {
                        if (logger.isInfoEnabled()) {
                            logger.info("JPAFacetDao.getFacets(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                        }
                    }
                }
            }
            else {
                try {
                    Class c = apiKey.getConnector().facetClass();
                    Method m = c.getMethod(methodName, EntityManager.class, ApiKey.class, ObjectType.class, Long.class, Integer.class, TagFilter.class);
                    facets = (List<AbstractFacet>)m.invoke(null, em, apiKey, null, timeInMillis, desiredCount, tagFilter);
                }
                catch (Exception ignored) {
                    if (logger.isInfoEnabled()) {
                        logger.info("JPAFacetDao.getFacets(): ignoring exception '" + ignored.getClass() + "' while trying to invoke method '" + methodName + "'");
                    }
                }
            }
        }
        return facets;
    }

    @Override
	public void deleteAllFacets(ApiKey apiKey) {
        final ObjectType[] objectTypes = apiKey.getConnector().objectTypes();
        for (ObjectType objectType : objectTypes) {
            deleteAllFacets(apiKey, objectType);
        }
	}

	@Override
	public void deleteAllFacets(ApiKey apiKey, ObjectType objectType) {
        if (objectType==null) {
            deleteAllFacets(apiKey);
        } else {
            // if facet has joins delete each facet one-by-one (this is a limitation of JPA)
            Class<? extends AbstractFacet> facetClass = getFacetClass(apiKey.getConnector(), objectType);
            if (JPAUtils.hasRelation(facetClass)) {
               deleteFacetsOneByOne(apiKey, facetClass);
            } else {
                bulkDeleteFacets(apiKey, facetClass);
            }
            final LocationFacet.Source locationFacetSource = getLocationFacetSource(facetClass);
            if (locationFacetSource != LocationFacet.Source.NONE) {
                deleteLocationData(apiKey);
                deleteVisitedCitiesData(apiKey);
            }
        }
	}

    private void deleteVisitedCitiesData(final ApiKey apiKey) {
        final String facetName = getEntityName(VisitedCity.class);
        String stmtString = "DELETE FROM " + facetName + " facet WHERE facet.apiKeyId=?";
        final Query query = em.createQuery(stmtString);
        query.setParameter(1, apiKey.getId());
        query.executeUpdate();
    }

    private void deleteLocationData(final ApiKey apiKey) {
        final String facetName = getEntityName(LocationFacet.class);
        String stmtString = "DELETE FROM " + facetName + " facet WHERE facet.apiKeyId=?";
        final Query query = em.createQuery(stmtString);
        query.setParameter(1, apiKey.getId());
        query.executeUpdate();
    }

    private LocationFacet.Source getLocationFacetSource(final Class<? extends AbstractFacet> facetClass) {
        final ObjectTypeSpec objectTypeSpec = facetClass.getAnnotation(ObjectTypeSpec.class);
        final LocationFacet.Source locationFacetSource = objectTypeSpec.locationFacetSource();
        return locationFacetSource;
    }

    private void deleteFacetsOneByOne(final ApiKey apiKey, final Class<? extends AbstractFacet> facetClass) {
        List<? extends AbstractFacet> facets = getAllFacets(apiKey, facetClass);
        for (AbstractFacet facet : facets) {
            final AbstractFacet merged = em.merge(facet);
            em.remove(merged);
        }
    }

    private List<? extends AbstractFacet> getAllFacets(final ApiKey apiKey, final Class<? extends AbstractFacet> facetClass) {
        final String facetName = getEntityName(facetClass);
        String queryString = "SELECT facet FROM " + facetName + " facet WHERE facet.apiKeyId=?";
        final TypedQuery<? extends AbstractFacet> query = em.createQuery(queryString, AbstractFacet.class);
        query.setParameter(1, apiKey.getId());
        List<? extends AbstractFacet> found = query.getResultList();
        return found;
    }

    private void bulkDeleteFacets(final ApiKey apiKey, final Class<? extends AbstractFacet> facetClass) {
        final String facetName = getEntityName(facetClass);
        String stmtString = "DELETE FROM " + facetName + " facet WHERE facet.apiKeyId=?";
        final Query query = em.createQuery(stmtString);
        query.setParameter(1, apiKey.getId());
        query.executeUpdate();
    }

    @Override
	@Transactional(readOnly=false)
	public void persist(Object o) {
		em.persist(o);
	}

	@Override
	@Transactional(readOnly=false)
	public void merge(Object o) {
		em.merge(o);
	}

}
