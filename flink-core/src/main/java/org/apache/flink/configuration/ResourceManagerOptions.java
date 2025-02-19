/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.configuration;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.annotation.docs.Documentation;
import org.apache.flink.configuration.description.Description;
import org.apache.flink.configuration.description.TextElement;

import java.time.Duration;

/** The set of configuration options relating to the ResourceManager. */
@PublicEvolving
public class ResourceManagerOptions {

    private static final String START_WORKER_RETRY_INTERVAL_KEY =
            "resourcemanager.start-worker.retry-interval";

    /** Timeout for jobs which don't have a job manager as leader assigned. */
    public static final ConfigOption<String> JOB_TIMEOUT =
            ConfigOptions.key("resourcemanager.job.timeout")
                    .stringType()
                    .defaultValue("5 minutes")
                    .withDescription(
                            "Timeout for jobs which don't have a job manager as leader assigned.");

    /** This option is not used any more. */
    @Deprecated
    public static final ConfigOption<Integer> LOCAL_NUMBER_RESOURCE_MANAGER =
            ConfigOptions.key("local.number-resourcemanager")
                    .intType()
                    .defaultValue(1)
                    .withDescription("The number of resource managers start.");

    /**
     * Defines the network port to connect to for communication with the resource manager. By
     * default, the port of the JobManager, because the same ActorSystem is used. Its not possible
     * to use this configuration key to define port ranges.
     */
    public static final ConfigOption<Integer> IPC_PORT =
            ConfigOptions.key("resourcemanager.rpc.port")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "Defines the network port to connect to for communication with the resource manager. By"
                                    + " default, the port of the JobManager, because the same ActorSystem is used."
                                    + " Its not possible to use this configuration key to define port ranges.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<Integer> MAX_SLOT_NUM =
            ConfigOptions.key("slotmanager.number-of-slots.max")
                    .intType()
                    .defaultValue(Integer.MAX_VALUE)
                    .withDescription(
                            "Defines the maximum number of slots that the Flink cluster allocates. This configuration option "
                                    + "is meant for limiting the resource consumption for batch workloads. It is not recommended to configure this option "
                                    + "for streaming workloads, which may fail if there are not enough slots. Note that this configuration option does not take "
                                    + "effect for standalone clusters, where how many slots are allocated is not controlled by Flink.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<Double> MAX_TOTAL_CPU =
            ConfigOptions.key("slotmanager.max-total-resource.cpu")
                    .doubleType()
                    .noDefaultValue()
                    .withDescription(
                            "Maximum cpu cores the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MAX_SLOT_NUM.key()
                                    + "'.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<MemorySize> MAX_TOTAL_MEM =
            ConfigOptions.key("slotmanager.max-total-resource.memory")
                    .memoryType()
                    .noDefaultValue()
                    .withDescription(
                            "Maximum memory size the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MAX_SLOT_NUM.key()
                                    + "'.");

    /**
     * The number of redundant task managers. Redundant task managers are extra task managers
     * started by Flink, in order to speed up job recovery in case of failures due to task manager
     * lost. Note that this feature is available only to the active deployments (native K8s, Yarn).
     */
    public static final ConfigOption<Integer> REDUNDANT_TASK_MANAGER_NUM =
            ConfigOptions.key("slotmanager.redundant-taskmanager-num")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "The number of redundant task managers. Redundant task managers are extra task managers "
                                    + "started by Flink, in order to speed up job recovery in case of failures due to task manager lost. "
                                    + "Note that this feature is available only to the active deployments (native K8s, Yarn).");

    /**
     * The maximum number of start worker failures (Native Kubernetes / Yarn) per minute before
     * pausing requesting new workers. Once the threshold is reached, subsequent worker requests
     * will be postponed to after a configured retry interval ({@link
     * #START_WORKER_RETRY_INTERVAL}).
     */
    public static final ConfigOption<Double> START_WORKER_MAX_FAILURE_RATE =
            ConfigOptions.key("resourcemanager.start-worker.max-failure-rate")
                    .doubleType()
                    .defaultValue(10.0)
                    .withDescription(
                            "The maximum number of start worker failures (Native Kubernetes / Yarn) per minute "
                                    + "before pausing requesting new workers. Once the threshold is reached, subsequent "
                                    + "worker requests will be postponed to after a configured retry interval ('"
                                    + START_WORKER_RETRY_INTERVAL_KEY
                                    + "').");

    /**
     * The time to wait before requesting new workers (Native Kubernetes / Yarn) once the max
     * failure rate of starting workers ({@link #START_WORKER_MAX_FAILURE_RATE}) is reached.
     */
    public static final ConfigOption<Duration> START_WORKER_RETRY_INTERVAL =
            ConfigOptions.key(START_WORKER_RETRY_INTERVAL_KEY)
                    .durationType()
                    .defaultValue(Duration.ofSeconds(3))
                    .withDescription(
                            "The time to wait before requesting new workers (Native Kubernetes / Yarn) once the "
                                    + "max failure rate of starting workers ('"
                                    + START_WORKER_MAX_FAILURE_RATE.key()
                                    + "') is reached.");

    @Documentation.ExcludeFromDocumentation(
            "This is an expert option, that we do not want to expose in the documentation")
    public static final ConfigOption<Duration> REQUIREMENTS_CHECK_DELAY =
            ConfigOptions.key("slotmanager.requirement-check.delay")
                    .durationType()
                    .defaultValue(Duration.ofMillis(50))
                    .withDescription("The delay of the resource requirements check.");

    @Documentation.ExcludeFromDocumentation(
            "This is an expert option, that we do not want to expose in the documentation")
    public static final ConfigOption<Duration> DECLARE_NEEDED_RESOURCE_DELAY =
            ConfigOptions.key("slotmanager.declare-needed-resource.delay")
                    .durationType()
                    .defaultValue(Duration.ofMillis(50))
                    .withDescription("The delay of the declare needed resources.");

    /**
     * The timeout for a slot request to be discarded, in milliseconds.
     *
     * @deprecated Use {@link JobManagerOptions#SLOT_REQUEST_TIMEOUT}.
     */
    @Deprecated
    public static final ConfigOption<Long> SLOT_REQUEST_TIMEOUT =
            ConfigOptions.key("slotmanager.request-timeout")
                    .longType()
                    .defaultValue(-1L)
                    .withDescription("The timeout for a slot request to be discarded.");

    /**
     * Time in milliseconds of the start-up period of a standalone cluster. During this time,
     * resource manager of the standalone cluster expects new task executors to be registered, and
     * will not fail slot requests that can not be satisfied by any current registered slots. After
     * this time, it will fail pending and new coming requests immediately that can not be satisfied
     * by registered slots. If not set, {@link #SLOT_REQUEST_TIMEOUT} will be used by default.
     */
    public static final ConfigOption<Long> STANDALONE_CLUSTER_STARTUP_PERIOD_TIME =
            ConfigOptions.key("resourcemanager.standalone.start-up-time")
                    .longType()
                    .defaultValue(-1L)
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Time in milliseconds of the start-up period of a standalone cluster. During this time, "
                                                    + "resource manager of the standalone cluster expects new task executors to be registered, and will not "
                                                    + "fail slot requests that can not be satisfied by any current registered slots. After this time, it will "
                                                    + "fail pending and new coming requests immediately that can not be satisfied by registered slots. If not "
                                                    + "set, %s will be used by default.",
                                            TextElement.code(
                                                    JobManagerOptions.SLOT_REQUEST_TIMEOUT.key()))
                                    .build());

    /**
     * The timeout for an idle task manager to be released, in milliseconds.
     *
     * @deprecated Use {@link #TASK_MANAGER_TIMEOUT}.
     */
    @Deprecated
    public static final ConfigOption<Long> SLOT_MANAGER_TASK_MANAGER_TIMEOUT =
            ConfigOptions.key("slotmanager.taskmanager-timeout")
                    .longType()
                    .defaultValue(30000L)
                    .withDescription("The timeout for an idle task manager to be released.");

    /** The timeout for an idle task manager to be released, in milliseconds. */
    public static final ConfigOption<Long> TASK_MANAGER_TIMEOUT =
            ConfigOptions.key("resourcemanager.taskmanager-timeout")
                    .longType()
                    .defaultValue(30000L)
                    .withDeprecatedKeys(SLOT_MANAGER_TASK_MANAGER_TIMEOUT.key())
                    .withDescription(
                            Description.builder()
                                    .text("The timeout for an idle task manager to be released.")
                                    .build());

    /**
     * Release task executor only when each produced result partition is either consumed or failed.
     *
     * <p>Currently, produced result partition is released when it fails or consumer sends close
     * request to confirm successful end of consumption and to close the communication channel.
     *
     * @deprecated The default value should be reasonable enough in all cases, this option is to
     *     fallback to older behaviour which will be removed or refactored in future.
     */
    @Deprecated
    public static final ConfigOption<Boolean> TASK_MANAGER_RELEASE_WHEN_RESULT_CONSUMED =
            ConfigOptions.key("resourcemanager.taskmanager-release.wait.result.consumed")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Release task executor only when each produced result partition is either consumed or failed. "
                                                    + "'True' is default. 'False' means that idle task executor release is not blocked "
                                                    + "by receiver confirming consumption of result partition "
                                                    + "and can happen right away after 'resourcemanager.taskmanager-timeout' has elapsed. "
                                                    + "Setting this option to 'false' can speed up task executor release but can lead to unexpected failures "
                                                    + "if end of consumption is slower than 'resourcemanager.taskmanager-timeout'.")
                                    .build());

    /**
     * Prefix for passing custom environment variables to Flink's master process. For example for
     * passing LD_LIBRARY_PATH as an env variable to the AppMaster, set:
     * containerized.master.env.LD_LIBRARY_PATH: "/usr/lib/native" in the flink-conf.yaml.
     */
    public static final String CONTAINERIZED_MASTER_ENV_PREFIX = "containerized.master.env.";

    /**
     * Similar to the {@see CONTAINERIZED_MASTER_ENV_PREFIX}, this configuration prefix allows
     * setting custom environment variables for the workers (TaskManagers).
     */
    public static final String CONTAINERIZED_TASK_MANAGER_ENV_PREFIX =
            "containerized.taskmanager.env.";

    /** Timeout for TaskManagers to register at the active resource managers. */
    public static final ConfigOption<Duration> TASK_MANAGER_REGISTRATION_TIMEOUT =
            ConfigOptions.key("resourcemanager.taskmanager-registration.timeout")
                    .durationType()
                    .defaultValue(TaskManagerOptions.REGISTRATION_TIMEOUT.defaultValue())
                    .withFallbackKeys(TaskManagerOptions.REGISTRATION_TIMEOUT.key())
                    .withDescription(
                            "Timeout for TaskManagers to register at the active resource managers. "
                                    + "If exceeded, active resource manager will release and try to "
                                    + "re-request the resource for the worker. If not configured, "
                                    + "fallback to '"
                                    + TaskManagerOptions.REGISTRATION_TIMEOUT.key()
                                    + "'.");

    /** Timeout for ResourceManager to recover all the previous attempts workers. */
    public static final ConfigOption<Duration> RESOURCE_MANAGER_PREVIOUS_WORKER_RECOVERY_TIMEOUT =
            ConfigOptions.key("resourcemanager.previous-worker.recovery.timeout")
                    .durationType()
                    .defaultValue(Duration.ofSeconds(0))
                    .withDescription(
                            "Timeout for resource manager to recover all the previous attempts workers. If exceeded,"
                                    + " resource manager will handle new resource requests by requesting new workers."
                                    + " If you would like to reuse the previous workers as much as possible, you should"
                                    + " configure a longer timeout time to wait for previous workers to register.");

    // ---------------------------------------------------------------------------------------------

    /** Not intended to be instantiated. */
    private ResourceManagerOptions() {}
}
