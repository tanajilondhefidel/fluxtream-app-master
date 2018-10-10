package com.fluxtream.connectors;

import java.io.IOException;
import com.fluxtream.Configuration;
import com.fluxtream.utils.HttpUtils;
import com.fluxtream.utils.Utils;
import com.fluxtream.aspects.FlxLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Candide Kemmler (candide@fluxtream.com)
 */
@Component
public class OAuth2Helper {

    @Autowired
    Configuration env;

    FlxLogger logger = FlxLogger.getLogger(OAuth2Helper.class);

    public boolean revokeRefreshToken(long guestId, Connector connector,
                                      String removeRefreshTokenURL) {
        try {
            HttpUtils.fetch(removeRefreshTokenURL);
        }
        catch (Throwable e) {
            StringBuilder sb = new StringBuilder("module=API component=OAuth2Helper action=revokeRefreshToken")
                                .append(" message=\"attempt to revoke token failed\" connector=")
                                .append(connector.getName()).append(" guestId=")
                                .append(guestId).append(" url=").append(removeRefreshTokenURL)
                                .append(" stackTrace=<![CDATA[").append(Utils.stackTrace(e)).append("]]>");
            logger.error(sb.toString());
            return false;
        }
        return true;
    }
}
