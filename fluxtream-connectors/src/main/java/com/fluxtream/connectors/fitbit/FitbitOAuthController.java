package com.fluxtream.connectors.fitbit;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import com.fluxtream.Configuration;
import com.fluxtream.aspects.FlxLogger;
import com.fluxtream.auth.AuthHelper;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.connectors.SignpostOAuthHelper;
import com.fluxtream.connectors.controllers.ControllerSupport;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.Guest;
import com.fluxtream.services.ApiDataService;
import com.fluxtream.services.GuestService;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/fitbit")
public class FitbitOAuthController {

    FlxLogger logger = FlxLogger.getLogger(FitbitOAuthController.class);

	@Autowired
	GuestService guestService;

	@Autowired
	ApiDataService apiDataService;

	@Autowired
	SignpostOAuthHelper signpostHelper;

	@Autowired
	Configuration env;
	private static final String FITBIT_OAUTH_CONSUMER = "fitbitOAuthConsumer";
	private static final String FITBIT_OAUTH_PROVIDER = "fitbitOAuthProvider";
    private static final String FITBIT_RENEWTOKEN_APIKEYID = "fitbit.renewtoken.apiKeyId";
	public static final String GET_USER_PROFILE_CALL = "FITBIT_GET_USER_PROFILE_CALL";

	static {
		ObjectType.registerCustomObjectType(GET_USER_PROFILE_CALL);
	}

	@RequestMapping(value = "/token")
	public String getToken(HttpServletRequest request) throws IOException, ServletException,
			OAuthMessageSignerException, OAuthNotAuthorizedException,
			OAuthExpectationFailedException, OAuthCommunicationException {

		String oauthCallback = ControllerSupport.getLocationBase(request, env) + "fitbit/upgradeToken";
		if (request.getParameter("guestId") != null)
			oauthCallback += "?guestId=" + request.getParameter("guestId");

		String consumerKey = env.get("fitbitConsumerKey");
		String consumerSecret = env.get("fitbitConsumerSecret");

		OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey,
				consumerSecret);

		OAuthProvider provider = new DefaultOAuthProvider(
				"http://api.fitbit.com/oauth/request_token",
				"http://api.fitbit.com/oauth/access_token",
				"http://api.fitbit.com/oauth/authorize");

		request.getSession().setAttribute(FITBIT_OAUTH_CONSUMER, consumer);
		request.getSession().setAttribute(FITBIT_OAUTH_PROVIDER, provider);
        
        if (request.getParameter("apiKeyId")!=null)
            request.getSession().setAttribute(FITBIT_RENEWTOKEN_APIKEYID,
                                              request.getParameter("apiKeyId"));

		String approvalPageUrl = provider.retrieveRequestToken(consumer,
				oauthCallback);

		return "redirect:" + approvalPageUrl;
	}

	@RequestMapping(value = "/upgradeToken")
	public String upgradeToken(HttpServletRequest request) throws Exception {
		OAuthConsumer consumer = (OAuthConsumer) request.getSession()
				.getAttribute(FITBIT_OAUTH_CONSUMER);
		OAuthProvider provider = (OAuthProvider) request.getSession()
				.getAttribute(FITBIT_OAUTH_PROVIDER);
		String verifier = request.getParameter("oauth_verifier");
		provider.retrieveAccessToken(consumer, verifier);
		Guest guest = AuthHelper.getGuest();

        ApiKey apiKey;
        if (request.getSession().getAttribute(FITBIT_RENEWTOKEN_APIKEYID)!=null) {
            final String apiKeyIdString = (String) request.getSession().getAttribute(FITBIT_RENEWTOKEN_APIKEYID);
            long apiKeyId = Long.valueOf(apiKeyIdString);
            apiKey = guestService.getApiKey(apiKeyId);
        } else
            apiKey = guestService.createApiKey(guest.getId(), connector());

        guestService.populateApiKey(apiKey.getId());
		guestService.setApiKeyAttribute(apiKey,
				"accessToken", consumer.getToken());
		guestService.setApiKeyAttribute(apiKey,
				"tokenSecret", consumer.getTokenSecret());

        if (request.getSession().getAttribute(FITBIT_RENEWTOKEN_APIKEYID)!=null) {
            request.getSession().removeAttribute(FITBIT_RENEWTOKEN_APIKEYID);
            return "redirect:/app/tokenRenewed/" + connector().getName();
        }
		return "redirect:/app/from/" + connector().getName();
	}

	private Connector connector() {
		Connector fitbitConnector = Connector.getConnector("fitbit");
		return fitbitConnector;
	}

}
