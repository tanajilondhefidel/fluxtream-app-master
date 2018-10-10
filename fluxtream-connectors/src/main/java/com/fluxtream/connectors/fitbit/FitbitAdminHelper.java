package com.fluxtream.connectors.fitbit;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.SignpostOAuthHelper;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.services.GuestService;

@Component(value="fitbitHelper")
public class FitbitAdminHelper {

	@Autowired
	SignpostOAuthHelper signpostHelper;

	@Autowired
	GuestService guestService;
	
	public JSONArray getApiSubscriptions(long guestId) throws Exception {
		ApiKey apiKey = guestService.getApiKey(guestId, Connector.getConnector("fitbit"));
		
		String json = signpostHelper
				.makeRestCall(apiKey, -10,
						"http://api.fitbit.com/1/user/-/apiSubscriptions.json");

		JSONObject wrapper = JSONObject.fromObject(json);
		JSONArray jsonSubscriptions = wrapper.getJSONArray("apiSubscriptions");
		return jsonSubscriptions;
	}
	
	public JSONArray deleteApiSubscription(long guestId) throws Exception {
		ApiKey apiKey = guestService.getApiKey(guestId, Connector.getConnector("fitbit"));
		
		String json = signpostHelper
				.makeRestCall(apiKey, -10,
						"http://api.fitbit.com/1/user/-/apiSubscriptions.json");

		JSONObject wrapper = JSONObject.fromObject(json);
		JSONArray jsonSubscriptions = wrapper.getJSONArray("apiSubscriptions");
		return jsonSubscriptions;
	}
	
}
