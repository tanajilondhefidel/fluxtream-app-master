package com.fluxtream.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Query;
import com.fluxtream.aspects.FlxLogger;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.connectors.annotations.ObjectTypeSpec;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.Type;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;
import org.jetbrains.annotations.Nullable;

@MappedSuperclass
@Indexed
public abstract class AbstractFacet extends AbstractEntity {
    private static final FlxLogger LOG_DEBUG = FlxLogger.getLogger("Fluxtream");

    private static final String TAG_DELIMITER = ",";

    public AbstractFacet() {
        this.timeUpdated = System.currentTimeMillis();
        figureOutObjectType();
    }

    private void figureOutObjectType() {
        ObjectTypeSpec objectType = this.getClass().getAnnotation(ObjectTypeSpec.class);
        if (objectType!=null)
            this.objectType = objectType.value();
        else
            this.objectType = -1;
    }

    public AbstractFacet(Long apiKeyId) {
        this.timeUpdated = System.currentTimeMillis();
        this.apiKeyId = apiKeyId;
        figureOutObjectType();
	}

    @Index(name = "apiKey")
    public Long apiKeyId;

    @Index(name="guestId_index")
	@Field
	public long guestId;
	
	@Type(type="yes_no")
	@Index(name="isEmpty_index")
	public boolean isEmpty = false;
	
	@Index(name="timeUpdated_index")
	public long timeUpdated;
	
	@Field(index=org.hibernate.search.annotations.Index.UN_TOKENIZED, store=Store.YES)
	@Index(name="start_index")
	public long start;
	
	@Index(name="end_index")
	public long end;
	@Index(name="api_index")
	public int api;
	@Index(name="objectType_index")
	public int objectType;

    /**
     * A string representation of the tags for this facet.  You should NEVER set this field directly.  Instead, always
     * use the {@link #addTags} method which sets both this and the {@link #tagSet} fields.
     */
	@Lob
    public String tags;

    /**
     * A {@link Set} representation of the tags for this facet.  You should NEVER set this field directly.  Instead,
     * always use the {@link #addTags} method which sets both this and the {@link #tags} fields.
     */
    public transient Set<Tag> tagSet;

	@Lob
	@Field(index=org.hibernate.search.annotations.Index.TOKENIZED, store=Store.YES)
	public String comment;
	
	@Field(index=org.hibernate.search.annotations.Index.TOKENIZED, store=Store.YES)
	@Lob
	public String fullTextDescription;

    @PostLoad
    void loadTags() {
        if (tags == null || tags.equals("")) {
            return;
        }
        StringTokenizer st = new StringTokenizer(tags,", \t\n\r\f");
        while (st.hasMoreTokens()) {
            String tag = st.nextToken().trim();
            if (tag.length() > 0) {
                addTag(tag);
            }
        }
    }

    private void addTag(final String tagName) {
        if (tagName != null && tagName.length() > 0) {
            if (tagSet == null) {
                tagSet = new HashSet<Tag>();
            }
            Tag tag = new Tag();
            tag.name = tagName;
            tagSet.add(tag);
        }
    }

    protected void persistTags() {
        buildTagsStringFromTagsSet();
    }

    private void buildTagsStringFromTagsSet() {
        if (tagSet == null) {
            return;
        }
        if (tagSet.size() > 0) {
            final StringBuilder sb = new StringBuilder(TAG_DELIMITER);
            for (final Tag tag : tagSet) {
                if (tag.name.length() > 0) {
                    sb.append(tag.name).append(TAG_DELIMITER);
                }
            }
            if (sb.length() > 1) {
                tags = sb.toString();
            }
        }
        else {
            tags = "";
        }
    }

    @PrePersist
    @PreUpdate
    protected void setFullTextDescription() {
        this.fullTextDescription = null;
        makeFullTextIndexable();
        if (this.comment != null) {
            if (this.fullTextDescription == null) {
                this.fullTextDescription = "";
            }
            this.fullTextDescription += " " + this.comment;
            this.fullTextDescription = this.fullTextDescription.trim();
        }
        persistTags();
    }

    /** Clears this instance's tags. */
    public void clearTags() {
        if (tagSet != null) {
            tagSet.clear();
        }
        tags = "";
    }

    /**
     * Clears this instance's existing tags, parses the given tags {@link String} which is delimited by the given
     * <code>delimiter</code>, replacing illegal characters with an underscore, and adds them to this instance's
     * {@link #tags} and {@link #tagSet} fields.  One should ALWAYS use this method instead of directly setting the
     * member fields.
     *
     * @see Tag#parseTags(String, char)
     */
    public void addTags(final String tagsStr, final char delimiter) {
        if (tagsStr != null && tagsStr.length() > 0) {
            // create the Set if necessary
            if (tagSet == null) {
                tagSet = new HashSet<Tag>();
            }

            tagSet.addAll(Tag.parseTags(tagsStr, delimiter));

            // build the String representation
            buildTagsStringFromTagsSet();
        }
    }

    /** Returns an {@link Collections#unmodifiableSet(Set) unmodifiable Set} of the tags for this facet. */
    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tagSet);
    }

    /** Returns an {@link SortedSet} of the tags for this facet. Modifying the returned set will have no effect on the facet's tags. */
    public SortedSet<String> getTagsAsStrings() {
        final SortedSet<String> tagStrings = new TreeSet<String>();
        if ((tagSet != null) && (!tagSet.isEmpty())) {
            for (final Tag tag : tagSet) {
                if (tag != null && tag.name.length() > 0) {
                    tagStrings.add(tag.name);
                }
            }
        }

        return tagStrings;
    }

    public boolean hasTags() {
        return tagSet != null && tagSet.size() > 0;
    }

    public static AbstractFacet getOldestFacet(EntityManager em, ApiKey apiKey, ObjectType objType) {
        return getOldestOrLatestFacet(em, apiKey, objType, "asc");
    }

    public static AbstractFacet getLatestFacet(EntityManager em, ApiKey apiKey, ObjectType objType){
        return getOldestOrLatestFacet(em, apiKey, objType, "desc");
    }

    private static AbstractFacet getOldestOrLatestFacet(EntityManager em, ApiKey apiKey, ObjectType objType, String sortOrder) {
        Class facetClass;
        if (objType != null) {
            facetClass = objType.facetClass();
        }
        else {
            facetClass = apiKey.getConnector().facetClass();
        }
        Entity entity = (Entity)facetClass.getAnnotation(Entity.class);
        Query query = em.createQuery("select facet from " + entity.name()
                                     + " facet where facet.guestId = "
                                     + apiKey.getGuestId() + " order by facet.end "
                                     + sortOrder + " limit 1");
        query.setMaxResults(1);
        final List resultList = query.getResultList();
        if (resultList != null && resultList.size() > 0) {
            return (AbstractFacet)resultList.get(0);
        }
        return null;
    }

    public static List<AbstractFacet> getFacetsBefore(EntityManager em,
                                                      ApiKey apiKey,
                                                      ObjectType objType,
                                                      Long timeInMillis,
                                                      Integer desiredCount) {
        return getFacetsBefore(em, apiKey, objType, timeInMillis, desiredCount, null);
    }

    public static List<AbstractFacet> getFacetsAfter(EntityManager em,
                                                     ApiKey apiKey,
                                                     ObjectType objType,
                                                     Long timeInMillis,
                                                     Integer desiredCount){
        return getFacetsAfter(em, apiKey, objType, timeInMillis, desiredCount, null);
    }

    public static List<AbstractFacet> getFacetsBefore(EntityManager em,
                                                      ApiKey apiKey,
                                                      ObjectType objType,
                                                      Long timeInMillis,
                                                      Integer desiredCount,
                                                      @Nullable final TagFilter tagFilter) {
        final Class facetClass = getFacetClass(apiKey, objType);
        final Entity entity = (Entity)facetClass.getAnnotation(Entity.class);
        final String additionalWhereClause = (tagFilter == null) ? "" : " AND (" + tagFilter.getWhereClause() + ")";
        final String queryStr = "select facet from " + entity.name()
                                + " facet where facet.guestId = " + apiKey.getGuestId()
                                + " and facet.start <= " + timeInMillis
                                + additionalWhereClause
                                + " order by facet.start desc limit " + desiredCount;
        final Query query = em.createQuery(queryStr);
        query.setMaxResults(desiredCount);
        return (List<AbstractFacet>)query.getResultList();
    }

    public static List<AbstractFacet> getFacetsAfter(EntityManager em,
                                                     ApiKey apiKey,
                                                     ObjectType objType,
                                                     Long timeInMillis,
                                                     Integer desiredCount,
                                                     @Nullable final TagFilter tagFilter){
        final Class facetClass = getFacetClass(apiKey, objType);
        final Entity entity = (Entity)facetClass.getAnnotation(Entity.class);
        final String additionalWhereClause = (tagFilter == null) ? "" : " AND (" + tagFilter.getWhereClause() + ")";
        final String queryStr = "select facet from " + entity.name()
                                + " facet where facet.guestId = " + apiKey.getGuestId()
                                + " and facet.start >= " + timeInMillis
                                + additionalWhereClause
                                + " order by facet.start asc limit " + desiredCount;
        final Query query = em.createQuery(queryStr);
        query.setMaxResults(desiredCount);
        return (List<AbstractFacet>)query.getResultList();
    }

    private static Class getFacetClass(final ApiKey apiKey, final ObjectType objType) {
        final Class facetClass;
        if (objType != null) {
            facetClass = objType.facetClass();
        }
        else {
            facetClass = apiKey.getConnector().facetClass();
        }
        return facetClass;
    }

    protected abstract void makeFullTextIndexable();
}
