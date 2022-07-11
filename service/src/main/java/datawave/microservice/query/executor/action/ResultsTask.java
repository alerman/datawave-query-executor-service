package datawave.microservice.query.executor.action;

import datawave.core.query.logic.CheckpointableQueryLogic;
import datawave.core.query.logic.QueryLogic;
import datawave.microservice.query.executor.QueryExecutor;
import datawave.microservice.query.remote.QueryRequest;
import datawave.microservice.query.storage.CachedQueryStatus;
import datawave.microservice.query.storage.QueryTask;
import datawave.microservice.query.storage.TaskKey;
import org.apache.accumulo.core.client.Connector;
import org.apache.log4j.Logger;

public class ResultsTask extends ExecutorTask {
    private static final Logger log = Logger.getLogger(ResultsTask.class);
    
    public ResultsTask(QueryExecutor source, QueryTask task) {
        super(source, task);
    }
    
    @Override
    public boolean executeTask(CachedQueryStatus queryStatus, Connector connector) throws Exception {
        
        assert (QueryRequest.Method.NEXT.equals(task.getAction()));
        
        boolean taskComplete = false;
        TaskKey taskKey = task.getTaskKey();
        String queryId = taskKey.getQueryId();
        
        QueryLogic<?> queryLogic = getQueryLogic(queryStatus.getQuery(), queryStatus.getCurrentUser());
        try {
            if (queryLogic instanceof CheckpointableQueryLogic && ((CheckpointableQueryLogic) queryLogic).isCheckpointable()) {
                CheckpointableQueryLogic cpQueryLogic = (CheckpointableQueryLogic) queryLogic;
                
                cpQueryLogic.setupQuery(connector, queryStatus.getConfig(), task.getQueryCheckpoint());
                
                log.debug("Pulling results for  " + task.getTaskKey() + ": " + task.getQueryCheckpoint());
                taskComplete = pullResults(queryLogic, queryStatus, false);
                if (!taskComplete) {
                    checkpoint(taskKey.getQueryKey(), cpQueryLogic);
                    taskComplete = true;
                }
            } else {
                Exception e = new IllegalStateException("Attempting to get results for an uninitialized, non-checkpointable query logic");
                cache.updateFailedQueryStatus(queryId, e);
                throw e;
            }
        } finally {
            try {
                queryLogic.close();
            } catch (Exception e) {
                log.error("Failed to close query logic", e);
            }
        }
        
        return taskComplete;
    }
    
}
