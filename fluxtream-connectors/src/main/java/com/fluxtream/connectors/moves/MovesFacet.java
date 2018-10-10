package com.fluxtream.connectors.moves;

import java.util.List;
import javax.persistence.MappedSuperclass;
import com.fluxtream.connectors.Connector;
import com.fluxtream.domain.AbstractFacet;
import org.hibernate.annotations.Index;

/**
 * User: candide
 * Date: 18/06/13
 * Time: 14:46
 */
@MappedSuperclass
public abstract class MovesFacet extends AbstractFacet {

    @Index(name = "date")
    public String date;

    public MovesFacet() {
        this.api = Connector.getConnector("moves").value();
    }

    public MovesFacet(long apiKeyId) {
        super(apiKeyId);
        this.api = Connector.getConnector("moves").value();
    }

    abstract void addActivity(MovesActivity activity);

    abstract void removeActivity(final MovesActivity movesActivity);

    abstract List<MovesActivity> getActivities();

    @Override
    protected void makeFullTextIndexable() {
    }
}
