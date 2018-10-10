package com.fluxtream.domain;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.MappedSuperclass;
import com.fluxtream.utils.TimeUtils;
import org.joda.time.LocalDate;

/**
 * User: candide
 * Date: 20/09/13
 * Time: 19:27
 */
@MappedSuperclass
public abstract class AbstractRepeatableFacet extends AbstractFacet {

    public Date startDate;
    public Date endDate;

    public boolean allDayEvent;

    public AbstractRepeatableFacet(final long apiKeyId) {
        super(apiKeyId);
    }

    public AbstractRepeatableFacet() {
        super();
    }

    public List<String> getRepeatedDates() {
        LocalDate currLocalDate = new LocalDate(startDate);
        final LocalDate endLocalDate = new LocalDate(endDate);
        List<String> dates = new ArrayList<String>();
        while(!currLocalDate.isAfter(endLocalDate)) {
            final String date = TimeUtils.dateFormatterUTC.print(currLocalDate);
            dates.add(date);
            currLocalDate = currLocalDate.plusDays(1);
        }
        return dates;
    }

}
