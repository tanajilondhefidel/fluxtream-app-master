package com.fluxtream.mvc.controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fluxtream.SimpleTimeInterval;
import com.fluxtream.auth.AuthHelper;
import com.fluxtream.aspects.FlxLogger;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.fluxtream.Configuration;
import com.fluxtream.TimeInterval;
import com.fluxtream.TimeUnit;
import com.fluxtream.connectors.vos.AbstractFacetVO;
import com.fluxtream.domain.AbstractFacet;
import com.fluxtream.services.ApiDataService;
import com.fluxtream.services.FullTextSearchService;
import com.fluxtream.services.MetadataService;

@Controller
public class SearchController {

	FlxLogger logger = FlxLogger.getLogger(SearchController.class);

	@Autowired
	FullTextSearchService searchService;

	@Autowired
	ApiDataService apiDataService;

	@Autowired
	MetadataService metadataService;

    @Autowired
    Configuration env;

    @RequestMapping("/search/{page}")
    public ModelAndView search(HttpServletRequest request,
                               HttpServletResponse response, @PathVariable("page") int page,
                               @RequestParam("q") String terms) throws Exception {
        logger.info("action=search");
		
		long guestId = AuthHelper.getGuestId();

		List<AbstractFacet> facets = searchService.searchFacetsIndex(guestId,
				terms);

		List<AbstractFacetVO<? extends AbstractFacet>> facetVos = new ArrayList<AbstractFacetVO<? extends AbstractFacet>>();
		TimeZone currentTimeZone = metadataService.getCurrentTimeZone(guestId);
		TimeInterval timeInterval = new SimpleTimeInterval(new Date().getTime(),
				System.currentTimeMillis(), TimeUnit.ARBITRARY, currentTimeZone);
		
		for (AbstractFacet facet : facets) {
			Class<? extends AbstractFacetVO<AbstractFacet>> jsonFacetClass = AbstractFacetVO
					.getFacetVOClass(facet);
			AbstractFacetVO<AbstractFacet> facetVo = jsonFacetClass
					.newInstance();
			facetVo.extractValues(facet, timeInterval, null);
			facetVos.add(facetVo);
		}

		int from = page * Integer.valueOf(env.get("SEARCH_PAGE_SIZE"));
		int to = (page + 1) * Integer.valueOf(env.get("SEARCH_PAGE_SIZE"));
		to = to < facetVos.size() ? to : facetVos.size();
		
		ModelAndView mav = new ModelAndView("calendar/list");
		
		mav.addObject("total", facetVos.size());
		facetVos = facetVos.subList(from, to);

		mav.addObject("facets", facetVos);
		mav.addObject("pageSize", Integer.valueOf(env.get("SEARCH_PAGE_SIZE")));
		mav.addObject("manyPages", Integer.valueOf(env.get("MANY_PAGES")));
		mav.addObject("from", from);
		mav.addObject("to", to);
		mav.addObject("page", page);
		mav.addObject("format", DateTimeFormat.forPattern("EEE, d MMM yyyy, HH:mm"));

		request.setAttribute("searchTerms", terms);
		mav.setViewName("calendar/list");
		return mav;
	}
	
}
