package com.fluxtream.mvc.controllers;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import com.fluxtream.Configuration;
import com.fluxtream.connectors.Connector;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.ApiUpdate;
import com.fluxtream.domain.ConnectorInfo;
import com.fluxtream.domain.Guest;
import com.fluxtream.domain.UpdateWorkerTask;
import com.fluxtream.mvc.models.admin.ConnectorInstanceModelFactory;
import com.fluxtream.services.ConnectorUpdateService;
import com.fluxtream.services.GuestService;
import com.fluxtream.services.SystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: candide
 * Date: 17/09/13
 * Time: 12:24
 */
@Controller
public class AdminController {

    @Autowired
    GuestService guestService;

    @Autowired
    ConnectorInstanceModelFactory connectorInstanceModelFactory;

    @Autowired
    ConnectorUpdateService connectorUpdateService;

    @Autowired
    Configuration env;

    @Autowired
    SystemService systemService;

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping(value = { "/admin" })
    public ModelAndView admin(HttpServletResponse response) throws Exception {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setDateHeader("Expires", 0);
        final ModelAndView mav = getAdminModel();
        final List<ConnectorInfo> connectors = systemService.getConnectors();
        mav.addObject("subview", "connectorHealthDashboard");
        mav.addObject("connectors", connectors);
        final List<Guest> allGuests = (List<Guest>)mav.getModel().get("allGuests");
        List<Map.Entry<Guest,List<List<ApiKey>>>> rows = new ArrayList<Map.Entry<Guest,List<List<ApiKey>>>>();
        ValueHolder synching = getSynchingUpdateWorkerTasks();
        final List<UpdateWorkerTask> tasks = connectorUpdateService.getAllScheduledUpdateWorkerTasks();
        long consumerTriggerRepeatInterval = Long.valueOf(env.get("consumer.trigger.repeatInterval"));
        ValueHolder due = getDueUpdateWorkerWorkerTasks(tasks, consumerTriggerRepeatInterval);
        ValueHolder overdue = getOverdueUpdateWorkerWorkerTasks(tasks, consumerTriggerRepeatInterval);

        for (Guest guest : allGuests) {
            List<List<ApiKey>> guestApiKeys = new ArrayList<List<ApiKey>>();
            for (ConnectorInfo connector : connectors) {
                final List<ApiKey> apiKeys = guestService.getApiKeys(guest.getId(), Connector.fromValue(connector.api));
                guestApiKeys.add(apiKeys);
            }
            final Map.Entry<Guest, List<List<ApiKey>>> guestListEntry = new AbstractMap.SimpleEntry<Guest, List<List<ApiKey>>>(guest, guestApiKeys);
            rows.add(guestListEntry);
        }
        mav.addObject("synching", synching);
        mav.addObject("tasksDue", due);
        mav.addObject("tasksOverdue", overdue);
        mav.addObject("rows", rows);
        return mav;
    }

    private ValueHolder getDueUpdateWorkerWorkerTasks(final List<UpdateWorkerTask> tasks, long consumerTriggerRepeatInterval) {
        ValueHolder overdue = new ValueHolder();
        for (UpdateWorkerTask task : tasks) {
            if (task.timeScheduled>System.currentTimeMillis()-consumerTriggerRepeatInterval) {
                if (overdue.get(task.apiKeyId)!=null)
                    overdue.put(task.apiKeyId, overdue.get(task.apiKeyId)+1);
                else
                    overdue.put(task.apiKeyId, 1);
            }
        }
        return overdue;
    }

    private ValueHolder getOverdueUpdateWorkerWorkerTasks(final List<UpdateWorkerTask> tasks, long consumerTriggerRepeatInterval) {
        ValueHolder due = new ValueHolder();
        for (UpdateWorkerTask task : tasks) {
            if (task.timeScheduled<=System.currentTimeMillis()-consumerTriggerRepeatInterval) {
                if (due.get(task.apiKeyId)!=null)
                    due.put(task.apiKeyId, due.get(task.apiKeyId)+1);
                else
                    due.put(task.apiKeyId, 1);
            }
        }
        return due;
    }

    private ValueHolder getSynchingUpdateWorkerTasks() {
        ValueHolder synching = new ValueHolder();
        final List<UpdateWorkerTask> tasks = connectorUpdateService.getAllSynchingUpdateWorkerTasks();
        for (UpdateWorkerTask task : tasks) {
            if (synching.get(task.apiKeyId)!=null)
                synching.put(task.apiKeyId, synching.get(task.apiKeyId)+1);
            else
                synching.put(task.apiKeyId, 1);
        }
        return synching;
    }

    public class ValueHolder {
        Map<Long,Integer> dict = new HashMap<Long,Integer>();
        public void put(Long l, Integer i) { dict.put(l, i); }
        public Integer get(Long l) { return dict.get(l); }
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}/{objectTypes}/historyUpdate")
    public ModelAndView forceConnectorInstanceHistoryUpdate(@PathVariable("guestId") long guestId,
                                                            @PathVariable("apiKeyId") long apiKeyId,
                                                            @PathVariable("objectTypes") int objectTypes) {
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        connectorUpdateService.updateConnectorObjectType(apiKey, objectTypes, true, true);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}/{objectTypes}/refresh")
    public ModelAndView refreshConnectorInstance(@PathVariable("guestId") long guestId,
                                                 @PathVariable("apiKeyId") long apiKeyId,
                                                 @PathVariable("objectTypes") int objectTypes) {
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        connectorUpdateService.updateConnectorObjectType(apiKey, objectTypes, true, false);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    public ModelAndView getAdminModel() {
        ModelAndView mav = new ModelAndView("admin/index");
        final List<Guest> allGuests = guestService.getAllGuests();
        mav.addObject("allGuests", allGuests);
        mav.addObject("release", env.get("release"));
        return mav;
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping(value = "/admin/{guestId}/{apiKeyId}/setToPermanentFail")
    public ModelAndView setToPermanentFail(@PathVariable("guestId") long guestId,
                                           @PathVariable("apiKeyId") long apiKeyId) throws Exception {
        guestService.setApiKeyStatus(apiKeyId, ApiKey.Status.STATUS_PERMANENT_FAILURE, null);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}")
    public ModelAndView showConnectorInstanceDetails(@PathVariable("guestId") long guestId,
                                                     @PathVariable("apiKeyId") long apiKeyId) {
        ModelAndView mav = getAdminModel();
        mav.addObject("subview", "connectorDetails");
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        final Map<String, Object> connectorInstanceModel = connectorInstanceModelFactory.createConnectorInstanceModel(apiKey);
        final Guest guest = guestService.getGuestById(guestId);
        final List<ApiUpdate> lastUpdates = connectorUpdateService.getUpdates(apiKey, 100, 0);
        mav.addObject("guest", guest);
        mav.addObject("guestId", guest.getId());
        mav.addObject("apiKeyId", apiKeyId);
        mav.addObject("apiKey", apiKey);
        mav.addObject("connectorInstanceModel", connectorInstanceModel);
        mav.addObject("lastUpdates", lastUpdates);
        List<UpdateWorkerTask> scheduledTasks = getScheduledTasks(apiKey);
        mav.addObject("scheduledTasks", scheduledTasks);
        return mav;
    }

    private List<UpdateWorkerTask> getScheduledTasks(final ApiKey apiKey) {
        final int[] objectTypeValues = apiKey.getConnector().objectTypeValues();
        List<UpdateWorkerTask> scheduledTasks = new ArrayList<UpdateWorkerTask>();
        if (apiKey.getConnector().isAutonomous()) {
            final List<UpdateWorkerTask> updateWorkerTasks = connectorUpdateService.getUpdateWorkerTasks(apiKey, 0, 10);
            scheduledTasks.addAll(updateWorkerTasks);
        } else {
            for (int objectTypeValue : objectTypeValues) {
                final List<UpdateWorkerTask> updateWorkerTasks = connectorUpdateService.getUpdateWorkerTasks(apiKey, objectTypeValue, 10);
                scheduledTasks.addAll(updateWorkerTasks);
            }
        }
        Collections.sort(scheduledTasks, new Comparator<UpdateWorkerTask>() {

            @Override
            public int compare(final UpdateWorkerTask o1, final UpdateWorkerTask o2) {
                return (int)(o2.timeScheduled - o1.timeScheduled);
            }
        });
        return scheduledTasks;
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}")
    public ModelAndView showUserApiKeys(@PathVariable("guestId") long guestId) {
        ModelAndView mav = getAdminModel();
        mav.addObject("subview", "allConnectors");
        final Guest guest = guestService.getGuestById(guestId);
        final List<ApiKey> apiKeys = guestService.getApiKeys(guest.getId());
        mav.addObject("username", guest.username);
        mav.addObject("guestId", guest.getId());
        mav.addObject("connectorInstanceModels", getConnectorInstanceModels(apiKeys));
        return mav;
    }

    private Object getConnectorInstanceModels(final List<ApiKey> apiKeys) {
        Map<Long, Map<String,Object>> connectorInstanceModels = new HashMap<Long, Map<String,Object>>();
        for (ApiKey key : apiKeys) {
            final Map<String, Object> connectorInstanceModel = connectorInstanceModelFactory.createConnectorInstanceModel(key);
            connectorInstanceModels.put(key.getId(), connectorInstanceModel);
        }
        return connectorInstanceModels;
    }

}
