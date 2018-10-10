package com.fluxtream.connectors.withings;

import javax.persistence.Entity;
import com.fluxtream.connectors.annotations.ObjectTypeSpec;
import com.fluxtream.domain.AbstractFacet;
import org.hibernate.search.annotations.Indexed;

/**
 * User: candide
 * Date: 17/05/13
 * Time: 12:39
 */
@Entity(name="Facet_WithingsHeartPulseMeasure")
@ObjectTypeSpec(name = "heart_pulse", value = 4, prettyname = "Smart Body Analyzer Heart Rate Measure")
@Indexed
public class WithingsHeartPulseMeasureFacet extends AbstractFacet {

    public float heartPulse;

    public WithingsHeartPulseMeasureFacet() {}

    public WithingsHeartPulseMeasureFacet(final long apiKeyId) {
        super(apiKeyId);
    }

    @Override
    protected void makeFullTextIndexable() {

    }
}
