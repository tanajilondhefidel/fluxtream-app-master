package com.fluxtream.mvc.controllers;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import com.fluxtream.Configuration;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.ObjectType;
import com.fluxtream.connectors.vos.AbstractFacetVO;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.services.ApiDataService;
import com.fluxtream.services.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: candide
 * Date: 21/09/13
 * Time: 15:46
 */
@Controller
@RequestMapping("/openGraph")
public class OpenGraphController {

    @Autowired
    ApiDataService apiDataService;

    @Autowired
    MetadataService metadataService;

    @Autowired
    Configuration env;

    @RequestMapping("/{encryptedParameters}.html")
    public ModelAndView index(@PathVariable("encryptedParameters") String encryptedParameters) throws UnsupportedEncodingException {
        String params = env.decrypt(encryptedParameters);
        params = URLDecoder.decode(params, "UTF-8");
        final String[] parameters = params.split("/");
        if (parameters.length!=3)
            throw new RuntimeException("Unexpected number of parameters: " + parameters.length);
        int api = Integer.valueOf(parameters[0]);
        int objectType = Integer.valueOf(parameters[1]);
        long facetId = Long.valueOf(parameters[2]);
        final AbstractFacetVO<AbstractFacet> facet = apiDataService.getFacet(api, objectType, facetId);
        final Connector connector = Connector.fromValue(facet.api);
        String facetName = String.format("%s.%s", connector.getName(), ObjectType.getObjectType(connector, facet.objectType));
        ModelAndView mav = new ModelAndView("openGraph/" + facetName);
        mav.addObject("facet", facet);
        mav.addObject("metadataService", metadataService);
        return mav;
    }
}
