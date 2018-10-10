package com.fluxtream.services.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import com.fluxtream.aspects.FlxLogger;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.updaters.AbstractUpdater;
import com.fluxtream.connectors.updaters.ScheduleResult;
import com.fluxtream.connectors.updaters.UpdateInfo.UpdateType;
import com.fluxtream.domain.ApiKey;
import com.fluxtream.domain.ApiNotification;
import com.fluxtream.domain.ApiUpdate;
import com.fluxtream.domain.ConnectorInfo;
import com.fluxtream.domain.UpdateWorkerTask;
import com.fluxtream.domain.UpdateWorkerTask.Status;
import com.fluxtream.services.ApiDataService;
import com.fluxtream.services.ConnectorUpdateService;
import com.fluxtream.services.GuestService;
import com.fluxtream.services.MetadataService;
import com.fluxtream.services.SystemService;
import com.fluxtream.utils.JPAUtils;
import org.joda.time.DateTimeConstants;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Component
@Transactional(readOnly=true)
public class ConnectorUpdateServiceImpl implements ConnectorUpdateService, InitializingBean, DisposableBean {

    static FlxLogger logger = FlxLogger.getLogger(ConnectorUpdateServiceImpl.class);

    private Map<Connector, AbstractUpdater> updaters = new Hashtable<Connector, AbstractUpdater>();

    @Autowired
    BeanFactory beanFactory;

    @Autowired
    ApiDataService apiDataService;

    @Autowired
    @Qualifier("updateWorkersExecutor")
    ThreadPoolTaskExecutor executor;

    @Autowired
    GuestService guestService;

    @Autowired
    SystemService systemService;

    @PersistenceContext
    EntityManager em;

    @Autowired
    MetadataService metadataService;

    @Override
    public void afterPropertiesSet() throws Exception {
        executor.setThreadGroupName("UpdateWorkers");
        executor.setThreadNamePrefix("UpdateWorker-");
    }

    /**
     * This makes sure that we are only executing Update Jobs that were
     * created while this server was alive
     */
    private final String SERVER_UUID = UUID.randomUUID().toString();

    /**
     * Update all the facet types for a given user and connector.
     * @param apiKey The apiKey for which we want to update facets
     * @param force force an update (sync now); if false, it means it was called by the background "cron" task
     * @return
     */
    @Override
    public List<ScheduleResult> updateConnector(final ApiKey apiKey, boolean force) {
        List<ScheduleResult> scheduleResults = new ArrayList<ScheduleResult>();
        // TODO: check if this connector type is enabled and supportsSync before calling update.
        // If it is disabled and/or does not support sync, don't try to update it.

        // if forcing an update (sync now), we actually want to flush the update requests
        // that have stacked up in the queue
        if (force)
            flushUpdateWorkerTasks(apiKey, false);

        // some connectors (e.g. the fitbit) need to decide what objectTypes to update by themselves;
        // for those, we pass 0 for the objectType parameter, which will be overridden by the connector's updater
        if (apiKey.getConnector().isAutonomous()) {
            final boolean historyUpdateCompleted = isHistoryUpdateCompleted(apiKey, 0);
            scheduleObjectTypeUpdate(apiKey, 0, scheduleResults, historyUpdateCompleted
                                                                             ? UpdateType.INCREMENTAL_UPDATE
                                                                             : UpdateType.INITIAL_HISTORY_UPDATE);
        } else {
            int[] objectTypeValues = apiKey.getConnector().objectTypeValues();
            ConnectorInfo connectorInfo = null;
            String connectorName = "unknown";
            try {
                connectorInfo = systemService.getConnectorInfo(apiKey.getConnector().getName());
                connectorName = connectorInfo.connectorName;
            }
            catch (Throwable e) {
                // This connector is not in Connector info; skip it
            }
            if (connectorInfo==null || !connectorInfo.enabled ||!connectorInfo.supportsSync) {
                logger.info("Not updating " + connectorName);
                return scheduleResults;
            }
            for (int objectTypes : objectTypeValues) {
                final boolean historyUpdateCompleted = isHistoryUpdateCompleted(apiKey, objectTypes);
                scheduleObjectTypeUpdate(apiKey, objectTypes, scheduleResults, historyUpdateCompleted
                                                                                           ? UpdateType.INCREMENTAL_UPDATE
                                                                                           : UpdateType.INITIAL_HISTORY_UPDATE);
            }
        }
        long guestId=0;
        if(apiKey !=null) {
            guestId = apiKey.getGuestId();
        }
        System.out.println("updateConnector: guestId=" + guestId + ", apiKey=" + apiKey);

        return scheduleResults;
    }

    @Override
    public boolean isHistoryUpdateCompleted(final ApiKey apiKey,
                                            int objectTypes) {
        List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.find(em, UpdateWorkerTask.class, "updateWorkerTasks.completed", Status.DONE, UpdateType.INITIAL_HISTORY_UPDATE, objectTypes, apiKey.getId());
        return updateWorkerTasks.size() > 0;
    }

    @Override
    public List<ScheduleResult> updateConnectorObjectType(ApiKey apiKey,
                                                          int objectTypes,
                                                          boolean force,
                                                          boolean historyUpdate) {
        List<ScheduleResult> scheduleResults = new ArrayList<ScheduleResult>();
        getUpdateWorkerTask(apiKey, objectTypes);
        // if forcing an update (sync now), we actually want to flush the update requests
        // that have stacked up in the queue
        if (force)
            flushUpdateWorkerTasks(apiKey, objectTypes, historyUpdate);
        UpdateType updateType = isHistoryUpdateCompleted(apiKey, objectTypes)
                ? UpdateType.INCREMENTAL_UPDATE
                : UpdateType.INITIAL_HISTORY_UPDATE;
        scheduleObjectTypeUpdate(apiKey, objectTypes, scheduleResults, updateType);
        return scheduleResults;
    }

    /**
     * Schedules a new update if there is no update for the user for this ObjectType and <code>force</code> is false
     * @param apiKey The apiKey for which we want to update a specific facet/object type
     * @param objectTypes the integer bitmask of object types to be updated
     * @param scheduleResults The result of adding the update will be added to the list. \result.type will be of type
     *                        ScheduleResult.ResultType. If there was a previously existing \result.type will be
     *                        ALREADY_SCHEDULED
     */
    private void scheduleObjectTypeUpdate(final ApiKey apiKey, int objectTypes,
                                          List<ScheduleResult> scheduleResults,
                                          UpdateType updateType) {
        ConnectorInfo connectorInfo = null;
        try {
            connectorInfo = systemService.getConnectorInfo(apiKey.getConnector().getName());
        }
        catch (Throwable e) {
            // This connector is not in Connector info; skip it
        }
        if (connectorInfo == null || !connectorInfo.supportsSync)
            return;

        UpdateWorkerTask updateWorkerTask = getUpdateWorkerTask(apiKey, objectTypes);
        if (updateWorkerTask != null)
            scheduleResults.add(new ScheduleResult(apiKey.getId(), apiKey.getConnector().getName(), objectTypes, ScheduleResult.ResultType.ALREADY_SCHEDULED, updateWorkerTask.timeScheduled));
        else {
            final ScheduleResult scheduleResult = scheduleUpdate(apiKey, objectTypes, updateType, System.currentTimeMillis());
            scheduleResults.add(scheduleResult);
        }
    }

    @Override
    @Transactional(readOnly=false)
    public void cleanupStaleData() {
        // Keeping one week seems to lead to api/connectors/installed being too slow as of December 2013.
        // Reduce to two days
        long twoDaysAgo = System.currentTimeMillis() - (DateTimeConstants.MILLIS_PER_DAY*2);
        final Query cleanupUpdateWorkerTasks = em.createNativeQuery(String.format("DELETE FROM UpdateWorkerTask WHERE not(status=2 AND updateType=2) and timeScheduled<%s", twoDaysAgo));
        final int updateWorkerTasksDeleted = cleanupUpdateWorkerTasks.executeUpdate();
        System.out.println("deleted " + updateWorkerTasksDeleted + " UpdateWorkerTasks");
        final Query cleanupApiUpdates = em.createNativeQuery(String.format("DELETE FROM ApiUpdates WHERE ts<%s", twoDaysAgo));
        final int apiUpdatesDeleted = cleanupApiUpdates.executeUpdate();
        System.out.println("deleted " + apiUpdatesDeleted + " ApiUpdates");
    }

    /**
     * Calls updateConnector(...) for all of a guest's connector
     * @param guestId
     * @return a list of objects that describe worker tasks that have been either modified or added
     * to the update queue
     */
    public void cleanupStaleData(final long guestId) {
        final List<ApiKey> connectors = guestService.getApiKeys(guestId);
        for (ApiKey key : connectors) {
            if (key!=null && key.getConnector()!=null) {
                // cleanup previously executed tasks
                cleanupUpdateWorkerTasks(key);
            }
        }
    }

    /**
     * Calls updateConnector(...) for all of a guest's connector
     * @param guestId
     * @return a list of objects that describe worker tasks that have been either modified or added
     * to the update queue
     */
    @Override
    public List<ScheduleResult> updateAllConnectors(final long guestId, boolean force) {
        List<ScheduleResult> scheduleResults = new ArrayList<ScheduleResult>();
        final List<ApiKey> connectors = guestService.getApiKeys(guestId);
        for (ApiKey key : connectors) {
            // Make sure the connector is of a type which is still supported.  Otherwise
            // skip trying to update it.
            try {
                if (key != null && key.getConnector() != null) {
                    final ConnectorInfo connectorInfo = systemService.getConnectorInfo(key.getConnector().getName());
                    // Make sure that this connector type supports sync and is enabled in this Fluxtream instance
                    if (connectorInfo.supportsSync && connectorInfo.enabled && key.getStatus() != ApiKey.Status.STATUS_PERMANENT_FAILURE) {
                        List<ScheduleResult> updateRes = updateConnector(key, force);
                        scheduleResults.addAll(updateRes);
                    }
                }
            }
            catch (Throwable e) {
                // Ignore this connector
            }
        }
        return scheduleResults;
    }

    @Transactional(readOnly = false)
    @Override
    public ScheduleResult reScheduleUpdateTask(long updateWorkerTaskId, long time, boolean incrementRetries,
                                               UpdateWorkerTask.AuditTrailEntry auditTrailEntry) {
        UpdateWorkerTask updt = em
                .find(UpdateWorkerTask.class, updateWorkerTaskId);

        // Check if updt is null.  This can happen if we were in the process of updating a
        // connector instance which was subsequently deleted.  In that case, print a message
        // and return
        if(updt==null) {
            StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=reScheduleUpdateTask")
                        .append(" updateWorkerTaskId="+updateWorkerTaskId)
                        .append(" message=\"Ignoring reschedule of an update task which is no longer in the system (deleted connector?)");
            logger.info(sb);
            return null;
        }

        // Set the audit trail according to what just happened if a non-null auditTrailEntry is provided
        if(auditTrailEntry!=null)
            updt.addAuditTrailEntry(auditTrailEntry);

        // Spawn a duplicate entry in the UpdateWorker table to record this failure and the reason for it
        if (!incrementRetries) {
            UpdateWorkerTask failed = new UpdateWorkerTask(updt);
            failed.auditTrail = updt.auditTrail;
            failed.apiKeyId = updt.apiKeyId;
            failed.retries = updt.retries;
            failed.connectorName = updt.connectorName;
            failed.status = Status.FAILED;
            failed.guestId = updt.guestId;
            failed.timeScheduled = updt.timeScheduled;
            updt.retries = 0;
            em.persist(failed);
        } else
            updt.retries += 1;

        // Reschedule the original task
        updt.status = Status.SCHEDULED;

        // Reset serverUUID to UNCLAIMED to reflect the fact that this task is no longer in the process of being
        // executed.
        updt.serverUUID = UNCLAIMED;
        updt.timeScheduled = time;
        em.persist(updt);

        return new ScheduleResult(
                updt.apiKeyId,
                updt.connectorName,
                updt.getObjectTypes(),
                ScheduleResult.ResultType.SCHEDULED_UPDATE_DEFERRED,
                time);
    }

    @Override
    @Transactional(readOnly = false)
    public void setUpdateWorkerTaskStatus(long updateWorkerTaskId, Status status)
            throws RuntimeException {
        UpdateWorkerTask updt = em
                .find(UpdateWorkerTask.class, updateWorkerTaskId);
        // Check if updt is null.  This can happen if we were in the process of updating a
        // connector instance which was subsequently deleted.  In that case, print a message
        // and return
        if (updt == null) {
            StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=setUpdateWorkerTaskStatus")
                        .append(" updateWorkerTaskId="+updateWorkerTaskId)
                        .append(" message=\"Ignoring set status for an update task which is no longer in the system (deleted connector?)");
            logger.info(sb);
        }
        else {
            updt.status = status;

            // If the status is in_progress, set serverUUID to the current one.
            // For anything other than IN_PROGRESS, set the serverUUID to null to reflect that it's not claimed
            if(status==Status.IN_PROGRESS) {
                updt.serverUUID = SERVER_UUID;
            } else {
                updt.serverUUID = UNCLAIMED;
            }

            em.persist(updt);
        }
    }

    @Override
    @Transactional(readOnly = false)
    public void pollScheduledUpdateWorkerTasks() {

        int maxThreads = executor.getMaxPoolSize();
        int activeThreads = executor.getActiveCount();
        int availableThreads = maxThreads - activeThreads;

        List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.findWithLimit(em, UpdateWorkerTask.class,
                                                                          "updateWorkerTasks.byStatus",
                                                                          0, availableThreads,
                                                                          Status.SCHEDULED,
                                                                          System.currentTimeMillis());
        if (updateWorkerTasks.size() == 0) {
            logger.debug("module=updateQueue component=connectorUpdateService action=pollScheduledUpdateWorkerTasks message=\"Nothing to do\"");
            return;
        }

        StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=pollScheduleUpdates")
            .append(" availableThreads="+availableThreads)
            .append(" message=\"adding " + updateWorkerTasks.size() + " update worker tasks\"");

        logger.info(sb);

        for (int i=0; i<updateWorkerTasks.size(); i++) {
            UpdateWorkerTask updateWorkerTask = updateWorkerTasks.get(i);
            logger.info("module=updateQueue component=connectorUpdateService action=pollScheduledUpdateWorkerTasks" +
                        " message=\"Executing update: " +
                        " \"" + updateWorkerTask);

            // Claim can return false if the task with that ID has been deleted, such as in the case
            // of connector deletion.  In that case just skip this task.  If it returns true then
            // continue with task execution
            if(claim(updateWorkerTask.getId()) == true){
                UpdateWorker updateWorker = beanFactory.getBean(UpdateWorker.class);
                updateWorker.task = updateWorkerTask;
                try {
                    executor.execute(updateWorker);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    @Override
    public void addUpdater(Connector connector, AbstractUpdater updater) {
        updaters.put(connector, updater);
    }

    @Override
    public AbstractUpdater getUpdater(Connector connector) {
        return beanFactory.getBean(connector.getUpdaterClass());
    }

    @Override
    @Transactional(readOnly = false)
    public ScheduleResult scheduleUpdate(final ApiKey apiKey,
                                         int objectTypes, UpdateType updateType, long timeScheduled,
                                         String... jsonParams) {
        UpdateWorkerTask updateScheduled = getUpdateWorkerTask(apiKey, objectTypes);
        ScheduleResult scheduleResult = null;
        if (updateScheduled==null) {
            UpdateWorkerTask updateWorkerTask = new UpdateWorkerTask();
            updateWorkerTask.guestId = apiKey.getGuestId();
            updateWorkerTask.connectorName = apiKey.getConnector().getName();
            updateWorkerTask.apiKeyId = apiKey.getId();
            updateWorkerTask.objectTypes = objectTypes;
            updateWorkerTask.updateType = updateType;
            updateWorkerTask.status = Status.SCHEDULED;
            updateWorkerTask.timeScheduled = timeScheduled;
            updateWorkerTask.serverUUID = UNCLAIMED;
            if (jsonParams!=null&&jsonParams.length>0)
                updateWorkerTask.jsonParams = jsonParams[0];
            em.persist(updateWorkerTask);
            long now = System.currentTimeMillis();
            scheduleResult = new ScheduleResult(apiKey.getId(), apiKey.getConnector().getName(), objectTypes,
                                                timeScheduled <= now
                                                ? ScheduleResult.ResultType.SCHEDULED_UPDATE_IMMEDIATE
                                                : ScheduleResult.ResultType.SCHEDULED_UPDATE_DEFERRED,
                                                timeScheduled);
        } else {
            scheduleResult = new ScheduleResult(apiKey.getId(), apiKey.getConnector().getName(), objectTypes,
                                                ScheduleResult.ResultType.ALREADY_SCHEDULED,
                                                updateScheduled.timeScheduled);
        }
        StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=scheduleUpdate")
                .append(" guestId=").append(apiKey.getGuestId())
                .append(" connectorName=").append(apiKey.getConnector().getName())
                .append(" objectTypes=").append(objectTypes)
                .append(" resultType=").append(scheduleResult.type.toString());
        logger.info(sb.toString());
        return scheduleResult;
    }

    @Override
    @Transactional(readOnly = false)
    public void addApiNotification(Connector connector, long guestId, String content) {
        ApiNotification notification = new ApiNotification();
        notification.api = connector.value();
        notification.guestId = guestId;
        notification.ts = System.currentTimeMillis();
        notification.content = content;
        em.persist(notification);
    }

    @Override
    @Transactional(readOnly = false)
    public void addApiUpdate(final ApiKey apiKey, int objectTypes, long ts, long elapsed, String query,
                             boolean success, Integer httpResponseCode, String reason) {
        ApiUpdate updt = new ApiUpdate();
        updt.guestId = apiKey.getGuestId();
        updt.api = apiKey.getConnector().value();
        updt.apiKeyId = apiKey.getId();
        updt.ts = System.currentTimeMillis();
        updt.query = query;
        updt.objectTypes = objectTypes;
        updt.elapsed = elapsed;
        updt.success = success;
        updt.httpResponseCode = httpResponseCode;
        updt.reason = reason;
        em.persist(updt);
    }

    @Override
    public ApiUpdate getLastUpdate(ApiKey apiKey) {
        return JPAUtils.findUnique(em, ApiUpdate.class, "apiUpdates.last", apiKey.getId());
    }

    @Override
    public ApiUpdate getLastSuccessfulUpdate(ApiKey apiKey) {
        return JPAUtils.findUnique(em, ApiUpdate.class, "apiUpdates.last.successful.byApi", apiKey.getId());
    }

    @Override
    public List<ApiUpdate> getUpdates(ApiKey apiKey, final int pageSize, final int page) {
        return JPAUtils.findPaged(em, ApiUpdate.class, "apiUpdates.last.paged", pageSize, page, apiKey.getId());
    }

    @Override
    public ApiUpdate getLastSuccessfulUpdate(ApiKey apiKey,
                                             int objectTypes) {
        if (objectTypes == -1)
            return getLastSuccessfulUpdate(apiKey);
        return JPAUtils.findUnique(em, ApiUpdate.class, "apiUpdates.last.successful.byApiAndObjectTypes", objectTypes, apiKey.getId());
    }

    @Override
    public List<UpdateWorkerTask> getUpdateWorkerTasks(final ApiKey apiKey, int objectTypes, int max) {
        final List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.findWithLimit(em, UpdateWorkerTask.class,
                                                                                "updateWorkerTasks.withObjectTypes", 0,
                                                                                max, objectTypes, apiKey.getId(),
                                                                                getLiveServerUUIDs());
        return updateWorkerTasks;
    }

    @Override
    public UpdateWorkerTask getUpdateWorkerTask(final ApiKey apiKey, int objectTypes) {
        UpdateWorkerTask updateWorkerTask = JPAUtils.findUnique(em,
                                                                UpdateWorkerTask.class, "updateWorkerTasks.withObjectTypes.isScheduled",
                                                                getLiveServerUUIDs(),
                                                                objectTypes,
                                                                apiKey.getId());
        if (updateWorkerTask!=null&&hasStalled(updateWorkerTask)) {
            updateWorkerTask.status = Status.STALLED;
            em.merge(updateWorkerTask);
            return null;
        }
        return updateWorkerTask;
    }

    @Override
    public List<UpdateWorkerTask> getAllSynchingUpdateWorkerTasks() {
        List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.find(em, UpdateWorkerTask.class, "updateWorkerTasks.all.synching", getLiveServerUUIDs());
        return updateWorkerTasks;
    }

    @Override
    public List<UpdateWorkerTask> getAllScheduledUpdateWorkerTasks() {
        List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.find(em, UpdateWorkerTask.class,
                                                                 "updateWorkerTasks.all.scheduled");
        return updateWorkerTasks;
    }

    @Override
    public List<UpdateWorkerTask> getScheduledUpdateWorkerTasksForConnectorNameBeforeTime(final String connectorName, long beforeTime) {
        List<UpdateWorkerTask> updateWorkerTasks = JPAUtils.find(em, UpdateWorkerTask.class,
                                                                                  "updateWorkerTasks.byStatus.andName",
                                                                                  Status.SCHEDULED,
                                                                                  connectorName,
                                                                                  beforeTime);
        return updateWorkerTasks;
    }

    @Override
    public List<UpdateWorkerTask> getScheduledOrInProgressUpdateTasks(final ApiKey apiKey) {
        // Get the tasks that are currently scheduled or in progress and either have the active
        List<UpdateWorkerTask> updateWorkerTask = JPAUtils.find(em, UpdateWorkerTask.class,
                                                                "updateWorkerTasks.isScheduledOrInProgress",
                                                                getLiveServerUUIDs(),
                                                                apiKey.getId());
        for (UpdateWorkerTask workerTask : updateWorkerTask) {
            if (hasStalled(workerTask)) {
                workerTask.status = Status.STALLED;
                em.merge(workerTask);
            }
        }
        return updateWorkerTask;
    }

    @Override
    public Collection<UpdateWorkerTask> getUpdatingUpdateTasks(final ApiKey apiKey) {
        List<UpdateWorkerTask> tasks = JPAUtils.find(em, UpdateWorkerTask.class, "updateWorkerTasks.isInProgressOrScheduledBefore",
                                                     System.currentTimeMillis(), getLiveServerUUIDs(),
                                                     apiKey.getId());
        HashMap<Integer, UpdateWorkerTask> seen = new HashMap<Integer, UpdateWorkerTask>();
        for(UpdateWorkerTask task : tasks)
        {
            if(hasStalled(task))
            {
                task.status = Status.STALLED;
                em.merge(task);
            }
            else
            {
                if(seen.containsKey(task.objectTypes))
                {
                    if(seen.get(task.objectTypes).timeScheduled < task.timeScheduled)
                        seen.put(task.objectTypes, task);
                }
                else
                {
                    seen.put(task.objectTypes, task);
                }
            }
        }
        return seen.values();
    }

    private List<String> getLiveOrUnclaimedServerUUIDs() {
        List<String> list = new ArrayList<String>();
        list.add(SERVER_UUID);
        list.add(UNCLAIMED);
        return list;
    }

    private List<String> getLiveServerUUIDs() {
        List<String> list = new ArrayList<String>();
        list.add(SERVER_UUID);
        return list;
    }

    private boolean hasStalled(UpdateWorkerTask updateWorkerTask) {
        return System.currentTimeMillis()-updateWorkerTask.timeScheduled>3600000;
    }

    /**
     * cleanup done tasks for a guest's connector
     * @param apiKey The apiKey for which we want to update facets
     */
    @Transactional(readOnly = false)
    public void cleanupUpdateWorkerTasks(final ApiKey apiKey) {
        final int tasksDeleted = JPAUtils.execute(em, "updateWorkerTasks.cleanup.byApi", apiKey.getId(), UpdateType.INITIAL_HISTORY_UPDATE);
        logger.info("module=updateQueue component=connectorUpdateService action=cleanupUpdateWorkerTasks" +
                    " deleted=" + tasksDeleted + " connector=" + apiKey.getConnector().getName());
        em.flush();
    }

    /**
     * delete pending tasks for a guest's connector
     * @param apiKey The apiKey for which we want to update facets
     * @param wipeOutHistory whether to delete everything including the initial history update that
     *                       we use to track whether we need to everything from scratch or just do so
     *                       incrementally
     */
    @Transactional(readOnly = false)
    @Override
    public void flushUpdateWorkerTasks(final ApiKey apiKey, boolean wipeOutHistory) {
        if (!wipeOutHistory) {
            // Here we want to leave the completed history updates but get rid of the scheduled
            // items for this apiKey.  That translates into deleting items with status=0.
             JPAUtils.execute(em, "updateWorkerTasks.delete.scheduledByApi",
                             apiKey.getId());
        }
        else {
            // Here we want to delete all update worker tasks relating to this
            // apiKey other than the ones that are currently in progress.
            // This happens asynchronously after connector deletion and
            // is executed by ApiDataCleanupWorker, or while servicing a request to
            // reset a connector.
            JPAUtils.execute(em, "updateWorkerTasks.delete.byApi", apiKey.getId(),
                             Status.IN_PROGRESS);
        }
    }

    /**
     * delete pending tasks for a guest's connector
     * @param apiKey The apiKey for which we want to update a specific facet/object type
     * @param wipeOutHistory whether to delete everything including the initial history update that
     *                       we use to track whether we need to everything from scratch or just do so
     *                       incrementally
     */
    @Transactional(readOnly = false)
    @Override
    public void flushUpdateWorkerTasks(final ApiKey apiKey, int objectTypes, boolean wipeOutHistory) {
        if (!wipeOutHistory) {
            // Here we want to leave the completed history updates but get rid of the scheduled
            // items for this apiKey.  That translates into deleting items with status=0.
            JPAUtils.execute(em, "updateWorkerTasks.delete.scheduledByApiAndObjectType",
                             apiKey.getId(),
                             objectTypes);
        }
        else {
            // Here we want to delete all update worker tasks relating to this
            // apiKey other than the ones that are currently in progress.
            // This happens asynchronously after connector deletion and
            // is executed by ApiDataCleanupWorker, or while servicing a request to
            // reset a connector.
            JPAUtils.execute(em, "updateWorkerTasks.delete.scheduledAndHistoryByApiAndObjectType",
                             apiKey.getId(),
                             objectTypes);
        }
    }

    @Override
    public long getTotalNumberOfUpdatesSince(Connector connector, long then) {
        return JPAUtils.count(em,
                              "apiUpdates.count.all.since", connector.value(), then);
    }

    @Override
    public long getNumberOfUpdatesSince(final long guestId, int connectorValue, long then) {
        return JPAUtils.count(em,
                              "apiUpdates.count.byGuest.since", guestId, connectorValue,
                              then);
    }

    @Override
    public Collection<UpdateWorkerTask> getLastFinishedUpdateTasks(ApiKey apiKey) {
        List<UpdateWorkerTask> tasks = JPAUtils.find(em, UpdateWorkerTask.class,
                                                     "updateWorkerTasks.getLastFinishedTask",
                                                     System.currentTimeMillis(),
                                                     apiKey.getId());
        HashMap<Integer, UpdateWorkerTask> seen = new HashMap<Integer, UpdateWorkerTask>();
        for(UpdateWorkerTask task : tasks)
        {
            if(seen.containsKey(task.objectTypes))
            {
                if(seen.get(task.objectTypes).timeScheduled < task.timeScheduled)
                    seen.put(task.objectTypes, task);
            }
            else
            {
                seen.put(task.objectTypes, task);
            }
        }
        return seen.values();
    }

    @Override
    @Transactional(readOnly=false)
    public boolean claim(final long taskId) {
        UpdateWorkerTask task = em.find(UpdateWorkerTask.class, taskId);

        // Check if task is null.  This can happen if we were in the process of updating a
        // connector instance which was subsequently deleted.  In that case, print a message
        // and return
        if(task==null) {
            StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=claim")
                        .append(" updateWorkerTaskId="+taskId)
                        .append(" message=\"Ignoring claim request for an update task which is no longer in the system (deleted connector?)");
            logger.info(sb);
            return false;

        } else {
            task.serverUUID = SERVER_UUID;
            task.status = Status.IN_PROGRESS;
            task.addAuditTrailEntry(new UpdateWorkerTask.AuditTrailEntry(new java.util.Date(), SERVER_UUID));
            em.persist(task);
            return true;
        }

    }

    @Override
    @Transactional(readOnly=false)
    public void addAuditTrail(final long updateWorkerTaskId, final UpdateWorkerTask.AuditTrailEntry auditTrailEntry) {
        UpdateWorkerTask task = em.find(UpdateWorkerTask.class, updateWorkerTaskId);

        // Check if task is null.  This can happen if we were in the process of updating a
        // connector instance which was subsequently deleted.  In that case, print a message
        // and return
        if(task==null) {
            StringBuilder sb = new StringBuilder("module=updateQueue component=connectorUpdateService action=addAuditTrail")
                        .append(" updateWorkerTaskId="+updateWorkerTaskId)
                        .append(" message=\"Ignoring addAuditTrail request for an update task which is no longer in the system (deleted connector?)");
            logger.info(sb);
        }
        else {
            task.addAuditTrailEntry(auditTrailEntry);
            em.persist(task);
        }
    }

    @Override
    public void destroy() throws Exception {
        executor.shutdown();
    }
}
