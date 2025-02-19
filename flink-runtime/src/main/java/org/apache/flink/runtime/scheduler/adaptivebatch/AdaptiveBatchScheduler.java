/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.flink.runtime.scheduler.adaptivebatch;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.JobException;
import org.apache.flink.runtime.checkpoint.CheckpointRecoveryFactory;
import org.apache.flink.runtime.checkpoint.CheckpointsCleaner;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.executiongraph.IOMetrics;
import org.apache.flink.runtime.executiongraph.IntermediateResult;
import org.apache.flink.runtime.executiongraph.JobStatusListener;
import org.apache.flink.runtime.executiongraph.MarkPartitionFinishedStrategy;
import org.apache.flink.runtime.executiongraph.ResultPartitionBytes;
import org.apache.flink.runtime.executiongraph.failover.flip1.FailoverStrategy;
import org.apache.flink.runtime.executiongraph.failover.flip1.RestartBackoffTimeStrategy;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.jobgraph.IntermediateResultPartitionID;
import org.apache.flink.runtime.jobgraph.JobEdge;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.jsonplan.JsonPlanGenerator;
import org.apache.flink.runtime.jobgraph.topology.DefaultLogicalResult;
import org.apache.flink.runtime.jobgraph.topology.DefaultLogicalTopology;
import org.apache.flink.runtime.jobgraph.topology.DefaultLogicalVertex;
import org.apache.flink.runtime.metrics.groups.JobManagerJobMetricGroup;
import org.apache.flink.runtime.scheduler.DefaultExecutionDeployer;
import org.apache.flink.runtime.scheduler.DefaultScheduler;
import org.apache.flink.runtime.scheduler.ExecutionGraphFactory;
import org.apache.flink.runtime.scheduler.ExecutionOperations;
import org.apache.flink.runtime.scheduler.ExecutionSlotAllocatorFactory;
import org.apache.flink.runtime.scheduler.ExecutionVertexVersioner;
import org.apache.flink.runtime.scheduler.VertexParallelismStore;
import org.apache.flink.runtime.scheduler.adaptivebatch.forwardgroup.ForwardGroup;
import org.apache.flink.runtime.scheduler.adaptivebatch.forwardgroup.ForwardGroupComputeUtil;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;
import org.apache.flink.runtime.scheduler.strategy.SchedulingStrategyFactory;
import org.apache.flink.runtime.shuffle.ShuffleMaster;
import org.apache.flink.util.concurrent.ScheduledExecutor;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * This scheduler decides the parallelism of JobVertex according to the data volume it consumes. A
 * dynamically built up ExecutionGraph is used for this purpose.
 */
public class AdaptiveBatchScheduler extends DefaultScheduler {

    private final DefaultLogicalTopology logicalTopology;

    private final VertexParallelismDecider vertexParallelismDecider;

    private final Map<JobVertexID, ForwardGroup> forwardGroupsByJobVertexId;

    private final Map<IntermediateDataSetID, BlockingResultInfo> blockingResultInfos;

    private final boolean hybridOnlyConsumeFinishedPartition;

    public AdaptiveBatchScheduler(
            final Logger log,
            final JobGraph jobGraph,
            final Executor ioExecutor,
            final Configuration jobMasterConfiguration,
            final Consumer<ComponentMainThreadExecutor> startUpAction,
            final ScheduledExecutor delayExecutor,
            final ClassLoader userCodeLoader,
            final CheckpointsCleaner checkpointsCleaner,
            final CheckpointRecoveryFactory checkpointRecoveryFactory,
            final JobManagerJobMetricGroup jobManagerJobMetricGroup,
            final SchedulingStrategyFactory schedulingStrategyFactory,
            final FailoverStrategy.Factory failoverStrategyFactory,
            final RestartBackoffTimeStrategy restartBackoffTimeStrategy,
            final ExecutionOperations executionOperations,
            final ExecutionVertexVersioner executionVertexVersioner,
            final ExecutionSlotAllocatorFactory executionSlotAllocatorFactory,
            long initializationTimestamp,
            final ComponentMainThreadExecutor mainThreadExecutor,
            final JobStatusListener jobStatusListener,
            final ExecutionGraphFactory executionGraphFactory,
            final ShuffleMaster<?> shuffleMaster,
            final Time rpcTimeout,
            final VertexParallelismDecider vertexParallelismDecider,
            int defaultMaxParallelism,
            boolean hybridOnlyConsumeFinishedPartition)
            throws Exception {

        super(
                log,
                jobGraph,
                ioExecutor,
                jobMasterConfiguration,
                startUpAction,
                delayExecutor,
                userCodeLoader,
                checkpointsCleaner,
                checkpointRecoveryFactory,
                jobManagerJobMetricGroup,
                schedulingStrategyFactory,
                failoverStrategyFactory,
                restartBackoffTimeStrategy,
                executionOperations,
                executionVertexVersioner,
                executionSlotAllocatorFactory,
                initializationTimestamp,
                mainThreadExecutor,
                jobStatusListener,
                executionGraphFactory,
                shuffleMaster,
                rpcTimeout,
                computeVertexParallelismStoreForDynamicGraph(
                        jobGraph.getVertices(), defaultMaxParallelism),
                new DefaultExecutionDeployer.Factory());

        this.logicalTopology = DefaultLogicalTopology.fromJobGraph(jobGraph);

        this.vertexParallelismDecider = vertexParallelismDecider;

        this.forwardGroupsByJobVertexId =
                ForwardGroupComputeUtil.computeForwardGroups(
                        jobGraph.getVerticesSortedTopologicallyFromSources(),
                        getExecutionGraph()::getJobVertex);

        this.blockingResultInfos = new HashMap<>();

        this.hybridOnlyConsumeFinishedPartition = hybridOnlyConsumeFinishedPartition;
    }

    @Override
    protected void startSchedulingInternal() {
        initializeVerticesIfPossible();

        super.startSchedulingInternal();
    }

    @Override
    protected void onTaskFinished(final Execution execution, final IOMetrics ioMetrics) {
        checkNotNull(ioMetrics);
        updateResultPartitionBytesMetrics(ioMetrics.getResultPartitionBytes());
        initializeVerticesIfPossible();

        super.onTaskFinished(execution, ioMetrics);
    }

    private void updateResultPartitionBytesMetrics(
            Map<IntermediateResultPartitionID, ResultPartitionBytes> resultPartitionBytes) {
        checkNotNull(resultPartitionBytes);
        resultPartitionBytes.forEach(
                (partitionId, partitionBytes) -> {
                    IntermediateResult result =
                            getExecutionGraph()
                                    .getAllIntermediateResults()
                                    .get(partitionId.getIntermediateDataSetID());
                    checkNotNull(result);

                    blockingResultInfos.compute(
                            result.getId(),
                            (ignored, resultInfo) -> {
                                if (resultInfo == null) {
                                    resultInfo = createFromIntermediateResult(result);
                                }
                                resultInfo.recordPartitionInfo(
                                        partitionId.getPartitionNumber(), partitionBytes);
                                return resultInfo;
                            });
                });
    }

    @Override
    protected void resetForNewExecution(final ExecutionVertexID executionVertexId) {
        final ExecutionVertex executionVertex = getExecutionVertex(executionVertexId);
        if (executionVertex.getExecutionState() == ExecutionState.FINISHED) {
            executionVertex
                    .getProducedPartitions()
                    .values()
                    .forEach(
                            partition -> {
                                blockingResultInfos.computeIfPresent(
                                        partition.getIntermediateResult().getId(),
                                        (ignored, resultInfo) -> {
                                            resultInfo.resetPartitionInfo(
                                                    partition.getPartitionNumber());
                                            return resultInfo;
                                        });
                            });
        }

        super.resetForNewExecution(executionVertexId);
    }

    @Override
    protected MarkPartitionFinishedStrategy getMarkPartitionFinishedStrategy() {
        return (rp) ->
                // for blocking result partition case, it always needs mark
                // partition finished. for hybrid only consume finished partition case, as
                // downstream needs the producer's finished status to start consuming the produced
                // data, the notification of partition finished is required.
                rp.isBlockingOrBlockingPersistentResultPartition()
                        || hybridOnlyConsumeFinishedPartition;
    }

    void initializeVerticesIfPossible() {
        final List<ExecutionJobVertex> newlyInitializedJobVertices = new ArrayList<>();
        try {
            final long createTimestamp = System.currentTimeMillis();
            for (ExecutionJobVertex jobVertex : getExecutionGraph().getVerticesTopologically()) {
                maybeSetParallelism(jobVertex);
            }
            for (ExecutionJobVertex jobVertex : getExecutionGraph().getVerticesTopologically()) {
                if (canInitialize(jobVertex)) {
                    getExecutionGraph().initializeJobVertex(jobVertex, createTimestamp);
                    newlyInitializedJobVertices.add(jobVertex);
                }
            }
        } catch (JobException ex) {
            log.error("Unexpected error occurred when initializing ExecutionJobVertex", ex);
            failJob(ex, System.currentTimeMillis());
        }

        if (newlyInitializedJobVertices.size() > 0) {
            updateTopology(newlyInitializedJobVertices);
        }
    }

    private void maybeSetParallelism(final ExecutionJobVertex jobVertex) {
        if (jobVertex.isParallelismDecided()) {
            return;
        }

        Optional<List<BlockingResultInfo>> consumedResultsInfo =
                tryGetConsumedResultsInfo(jobVertex);
        if (!consumedResultsInfo.isPresent()) {
            return;
        }

        ForwardGroup forwardGroup = forwardGroupsByJobVertexId.get(jobVertex.getJobVertexId());
        int parallelism;

        if (forwardGroup != null && forwardGroup.isParallelismDecided()) {
            parallelism = forwardGroup.getParallelism();
            log.info(
                    "Parallelism of JobVertex: {} ({}) is decided to be {} according to forward group's parallelism.",
                    jobVertex.getName(),
                    jobVertex.getJobVertexId(),
                    parallelism);

        } else {
            parallelism =
                    vertexParallelismDecider.decideParallelismForVertex(consumedResultsInfo.get());
            if (forwardGroup != null) {
                forwardGroup.setParallelism(parallelism);
            }

            log.info(
                    "Parallelism of JobVertex: {} ({}) is decided to be {}.",
                    jobVertex.getName(),
                    jobVertex.getJobVertexId(),
                    parallelism);
        }

        changeJobVertexParallelism(jobVertex, parallelism);
    }

    private void changeJobVertexParallelism(ExecutionJobVertex jobVertex, int parallelism) {
        // update the JSON Plan, it's needed to enable REST APIs to return the latest parallelism of
        // job vertices
        jobVertex.getJobVertex().setParallelism(parallelism);
        try {
            getExecutionGraph().setJsonPlan(JsonPlanGenerator.generatePlan(getJobGraph()));
        } catch (Throwable t) {
            log.warn("Cannot create JSON plan for job", t);
            // give the graph an empty plan
            getExecutionGraph().setJsonPlan("{}");
        }

        jobVertex.setParallelism(parallelism);
    }

    /** Get information of consumable results. */
    private Optional<List<BlockingResultInfo>> tryGetConsumedResultsInfo(
            final ExecutionJobVertex jobVertex) {

        List<BlockingResultInfo> consumableResultInfo = new ArrayList<>();

        DefaultLogicalVertex logicalVertex = logicalTopology.getVertex(jobVertex.getJobVertexId());
        Iterable<DefaultLogicalResult> consumedResults = logicalVertex.getConsumedResults();

        for (DefaultLogicalResult consumedResult : consumedResults) {
            final ExecutionJobVertex producerVertex =
                    getExecutionJobVertex(consumedResult.getProducer().getId());
            if (producerVertex.isFinished()) {
                BlockingResultInfo resultInfo =
                        checkNotNull(blockingResultInfos.get(consumedResult.getId()));
                consumableResultInfo.add(resultInfo);
            } else {
                // not all inputs consumable, return Optional.empty()
                return Optional.empty();
            }
        }

        return Optional.of(consumableResultInfo);
    }

    private boolean canInitialize(final ExecutionJobVertex jobVertex) {
        if (jobVertex.isInitialized() || !jobVertex.isParallelismDecided()) {
            return false;
        }

        // all the upstream job vertices need to have been initialized
        for (JobEdge inputEdge : jobVertex.getJobVertex().getInputs()) {
            final ExecutionJobVertex producerVertex =
                    getExecutionGraph().getJobVertex(inputEdge.getSource().getProducer().getID());
            checkNotNull(producerVertex);
            if (!producerVertex.isInitialized()) {
                return false;
            }
        }

        return true;
    }

    private void updateTopology(final List<ExecutionJobVertex> newlyInitializedJobVertices) {
        for (ExecutionJobVertex vertex : newlyInitializedJobVertices) {
            initializeOperatorCoordinatorsFor(vertex);
        }

        // notify execution graph updated, and try to update the execution topology.
        getExecutionGraph().notifyNewlyInitializedJobVertices(newlyInitializedJobVertices);
    }

    private void initializeOperatorCoordinatorsFor(ExecutionJobVertex vertex) {
        operatorCoordinatorHandler.registerAndStartNewCoordinators(
                vertex.getOperatorCoordinators(), getMainThreadExecutor());
    }

    /**
     * Compute the {@link VertexParallelismStore} for all given vertices in a dynamic graph, which
     * will set defaults and ensure that the returned store contains valid parallelisms, with the
     * configured default max parallelism.
     *
     * @param vertices the vertices to compute parallelism for
     * @param defaultMaxParallelism the global default max parallelism
     * @return the computed parallelism store
     */
    @VisibleForTesting
    public static VertexParallelismStore computeVertexParallelismStoreForDynamicGraph(
            Iterable<JobVertex> vertices, int defaultMaxParallelism) {
        // for dynamic graph, there is no need to normalize vertex parallelism. if the max
        // parallelism is not configured and the parallelism is a positive value, max
        // parallelism can be computed against the parallelism, otherwise it needs to use the
        // global default max parallelism.
        return computeVertexParallelismStore(
                vertices,
                v -> {
                    if (v.getParallelism() > 0) {
                        return getDefaultMaxParallelism(v);
                    } else {
                        return defaultMaxParallelism;
                    }
                },
                Function.identity());
    }

    private static BlockingResultInfo createFromIntermediateResult(IntermediateResult result) {
        checkArgument(result != null);
        // Note that for dynamic graph, different partitions in the same result have the same number
        // of subpartitions.
        if (result.getConsumingDistributionPattern() == DistributionPattern.POINTWISE) {
            return new PointwiseBlockingResultInfo(
                    result.getId(),
                    result.getNumberOfAssignedPartitions(),
                    result.getPartitions()[0].getNumberOfSubpartitions());
        } else {
            return new AllToAllBlockingResultInfo(
                    result.getId(),
                    result.getNumberOfAssignedPartitions(),
                    result.getPartitions()[0].getNumberOfSubpartitions(),
                    result.isBroadcast());
        }
    }

    @VisibleForTesting
    BlockingResultInfo getBlockingResultInfo(IntermediateDataSetID resultId) {
        return blockingResultInfos.get(resultId);
    }
}
