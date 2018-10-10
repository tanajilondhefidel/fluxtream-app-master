package com.fluxtream.connectors.zeo;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import com.fluxtream.connectors.annotations.ObjectTypeSpec;
import com.fluxtream.domain.AbstractLocalTimeFacet;
import org.hibernate.search.annotations.Indexed;

@Entity(name="Facet_ZeoSleepStats")
@NamedQueries({
  @NamedQuery(name = "zeo.sleep.getNewest",
              query = "SELECT facet FROM Facet_ZeoSleepStats facet " +
                      "WHERE facet.guestId=? " +
                      "ORDER BY facet.start DESC")
})
@ObjectTypeSpec(name = "sleep", value = 1, parallel=true, prettyname = "Sleep", isDateBased = true)
@Indexed
public class ZeoSleepStatsFacet extends AbstractLocalTimeFacet {

    public int zq;
    public int awakenings;
    public int morningFeel;
    public int totalZ;
    public int timeInDeepPercentage;
    public int timeInLightPercentage;
    public int timeInRemPercentage;
    public int timeInWakePercentage;
    public int timeToZ;

    @Lob
    public String sleepGraph;

    public ZeoSleepStatsFacet() {
        super();
    }

    public ZeoSleepStatsFacet(final long apiKeyId) {
        super(apiKeyId);
    }

    @Override
    protected void makeFullTextIndexable() {}

}