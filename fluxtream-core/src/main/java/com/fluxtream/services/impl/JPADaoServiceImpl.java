package com.fluxtream.services.impl;

import java.util.List;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.services.JPADaoService;
import com.fluxtream.utils.JPAUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@Scope("singleton")
public class JPADaoServiceImpl implements JPADaoService {

	@PersistenceContext
	EntityManager em;

	@Override
	public <T> List<T> find(String queryName, Class<T> clazz, Object... params) {
		return JPAUtils.find(em, clazz, queryName, params);
	}

	@Override
	public <T> T findOne(String queryName, Class<T> clazz, Object... params) {
		return JPAUtils.findUnique(em, clazz, queryName, params);
	}

	@Override
	public <T> List<T> findWithLimit(String queryName, Class<T> clazz, int firstResult, int maxResults, Object... params) {
		return JPAUtils.findWithLimit(em, clazz, queryName, firstResult, maxResults, params);
	}

    @Override
    public <T> List<T> executeQueryWithLimit(final String queryString, final int limit, final Class<T> clazz, Object... params) {
        TypedQuery<T> query = em.createQuery(queryString, clazz);
        int i=1;
        if (params!=null) {
            for (Object param : params) {
                query.setParameter(i++, param);
            }
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
	public long countFacets(Connector connector, long guestId) {
		if (!connector.hasFacets()) return 0;
		ObjectType[] objectTypes = connector.objectTypes();
		long count = 0;
		if (objectTypes!=null&&objectTypes.length>0) {
			for (ObjectType objectType : objectTypes) {
				long nFacets = countFacets(objectType.facetClass(), guestId);
				count += nFacets;
			}
		} else {
			long nFacets = countFacets(connector.facetClass(), guestId);
			count += nFacets;
		}
		return count;
	}

    @Override
    public int execute(final String jpql) {
        Query query = em.createQuery(jpql);
        return query.executeUpdate();
    }

    private long countFacets(Class<? extends AbstractFacet> facetClass,
			long guestId) {
		Entity entityAnnotation = facetClass.getAnnotation(Entity.class);
		String entityName = entityAnnotation.name();
		String queryString = "SELECT count(e) FROM " + entityName + " e WHERE e.guestId=" + guestId;
		Query countQuery = em.createQuery(queryString);
		Object singleResult = countQuery.getSingleResult();
        return (Long) singleResult;
	}

   @Override
    public Long executeNativeQuery(final String queryString) {
        final Query nativeQuery = em.createNativeQuery(queryString);
       final Object singleResult = nativeQuery.getSingleResult();
       if (singleResult==null) return null;
       return ((Number)singleResult).longValue();
    }

    @Override
    public List executeNativeQuery(final String s, Object... params) {
        final Query query = em.createNativeQuery(s);
        int i=1;
        for (Object param : params)
            query.setParameter(i++, param);
        return query.getResultList();
    }

    @Override
	@Transactional(readOnly=false)
	public void persist(Object o) {
		em.persist(o);
	}

	@Override
	@Transactional(readOnly=false)
	public void remove(Class<?> clazz, long id) {
		Object o = em.find(clazz, id);
		em.remove(o);
	}

}