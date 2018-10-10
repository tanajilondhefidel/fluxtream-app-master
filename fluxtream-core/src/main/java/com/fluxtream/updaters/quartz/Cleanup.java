package com.fluxtream.updaters.quartz;

import com.fluxtream.aspects.FlxLogger;
import com.fluxtream.services.ApiDataService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: candide
 * Date: 15/05/13
 * Time: 16:56
 */
public class Cleanup {

    static FlxLogger logger = FlxLogger.getLogger(Cleanup.class);

    @Autowired
    ApiDataService apiDataService;

    public void doCleanup() {
        try {
            logger.info("component=cleanup action=doCleanup");
            apiDataService.deleteStaleData();
        }
        catch (ClassNotFoundException e) {
            logger.warn("component=cleanup action=doCleanup message=" + e.getMessage());
        }
    }

}
