/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;
import com.fasterxml.jackson.databind.JsonNode;
import com.dynatrace.diagnostics.pdk.PluginEnvironment.Host;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * A Monitor which polls the DebugUI of ruxit instances to collect measures about ruxit Agents.
 *
 * @author dominik.stadler
 */
public class ElasticsearchMonitor implements Monitor {
	private static final Logger log = Logger.getLogger(ElasticsearchMonitor.class.getName());

	/************************************** Config properties **************************/
	protected static final String ENV_CONFIG_USE_FULL_URL_CONFIGURATION = "useFullUrlConfiguration";
	protected static final String ENV_CONFIG_URL = "url";
	protected static final String ENV_CONFIG_PORT = "port";
	protected static final String ENV_CONFIG_PROTOCOL = "protocol";
	protected static final String ENV_CONFIG_USER = "user";
	protected static final String ENV_CONFIG_PASSWORD = "password";
	protected static final String ENV_CONFIG_TIMEOUT = "timeout";

	/************************************** Metric Groups **************************/
	protected static final String METRIC_GROUP_ELASTICSEARCH = "Elasticsearch Monitor";

	/************************************** Measures **************************/
	protected static final String MSR_NODE_COUNT = "NodeCount";
	protected static final String MSR_DATA_NODE_COUNT = "DataNodeCount";

	protected static final String MSR_ACTIVE_PRIMARY_SHARDS = "ActivePrimaryShards";
	protected static final String MSR_ACTIVE_SHARDS_PERCENT = "ActiveShardsPercent";
	protected static final String MSR_ACTIVE_SHARDS = "ActiveShards";
	protected static final String MSR_RELOCATING_SHARDS = "RelocatingShards";
	protected static final String MSR_INITIALIZING_SHARDS = "InitializingShards";
	protected static final String MSR_UNASSIGNED_SHARDS = "UnassignedShards";
	protected static final String MSR_DELAYED_UNASSIGNED_SHARDS = "DelayedUnassignedShards";

	protected static final String MSR_MEM_INIT_HEAP = "InitHeap";
	protected static final String MSR_MEM_MAX_HEAP = "MaxHeap";
	protected static final String MSR_MEM_INIT_NON_HEAP = "InitNonHeap";
	protected static final String MSR_MEM_MAX_NON_HEAP = "MaxNonHeap";
	protected static final String MSR_MEM_MAX_DIRECT = "MaxDirect";

	protected static final String MSR_INDEX_COUNT = "IndexCount";
	protected static final String MSR_SHARD_COUNT = "ShardCount";

	protected static final String MSR_DOCUMENT_COUNT = "DocCount";
	protected static final String MSR_DELETED_COUNT = "DeletedCount";
    protected static final String MSR_DOCUMENT_COUNT_PER_SECOND = "DocCountPerSecond";
    protected static final String MSR_DELETED_COUNT_PER_SECOND = "DeletedCountPerSecond";

	protected static final String MSR_STORE_SIZE = "StoreSize";
	protected static final String MSR_STORE_THROTTLE_TIME = "StoreThrottleTime";
	protected static final String MSR_INDEXING_THROTTLE_TIME = "IndexingThrottleTime";
	protected static final String MSR_INDEXING_CURRENT = "IndexingCurrent";
	protected static final String MSR_DELETE_CURRENT = "DeleteCurrent";
	protected static final String MSR_QUERY_CURRENT = "QueryCurrent";
	protected static final String MSR_FETCH_CURRENT = "FetchCurrent";
	protected static final String MSR_SCROLL_CURRENT = "ScrollCurrent";
	protected static final String MSR_QUERY_CACHE_SIZE = "QueryCacheSize";
	protected static final String MSR_FIELD_DATA_SIZE = "FieldDataSize";
	protected static final String MSR_FIELD_DATA_EVICTIONS = "FieldDataEvictions";
	protected static final String MSR_PERCOLATE_SIZE = "PercolateSize";
	protected static final String MSR_TRANSLOG_SIZE = "TranslogSize";
	protected static final String MSR_REQUEST_CACHE_SIZE = "RequestCacheSize";
	protected static final String MSR_RECOVERY_THROTTLE_TIME = "RecoveryThrottleTime";
	protected static final String MSR_RECOVERY_AS_SOURCE = "RecoveryAsSource";
	protected static final String MSR_RECOVERY_AS_TARGET = "RecoveryAsTarget";

	protected static final String MSR_COMPLETION_SIZE = "CompletionSize";
	protected static final String MSR_SEGMENT_COUNT = "SegmentCount";
	protected static final String MSR_SEGMENT_SIZE = "SegmentSize";
	protected static final String MSR_FILE_DESCRIPTOR_COUNT = "FileDescriptorCount";
    protected static final String MSR_FILE_DESCRIPTOR_LIMIT = "FileDescriptorLimit";
	protected static final String MSR_FILE_SYSTEM_SIZE = "FileSystemSize";
	protected static final String MSR_PERCOLATE_COUNT = "PercolateCount";

	// for easier testing
	@SuppressWarnings("unused")
    protected static final String[] ALL_MEASURES  = new String[] {
			MSR_NODE_COUNT,
			MSR_DATA_NODE_COUNT,
			MSR_ACTIVE_PRIMARY_SHARDS,
			MSR_ACTIVE_SHARDS_PERCENT,
			MSR_ACTIVE_SHARDS,
			MSR_RELOCATING_SHARDS,
			MSR_INITIALIZING_SHARDS,
			MSR_UNASSIGNED_SHARDS,
			MSR_DELAYED_UNASSIGNED_SHARDS,
			MSR_MEM_INIT_HEAP,
			MSR_MEM_MAX_HEAP,
			MSR_MEM_INIT_NON_HEAP,
			MSR_MEM_MAX_NON_HEAP,
			MSR_MEM_MAX_DIRECT,
			MSR_INDEX_COUNT,
			MSR_SHARD_COUNT,
			MSR_DOCUMENT_COUNT,
			MSR_DELETED_COUNT,
            MSR_DOCUMENT_COUNT_PER_SECOND,
            MSR_DELETED_COUNT_PER_SECOND,

			MSR_INDEXING_THROTTLE_TIME,
			MSR_INDEXING_CURRENT,
			MSR_DELETE_CURRENT,
			MSR_QUERY_CURRENT,
			MSR_FETCH_CURRENT,
			MSR_SCROLL_CURRENT,
			MSR_PERCOLATE_SIZE,
			MSR_TRANSLOG_SIZE,
			MSR_REQUEST_CACHE_SIZE,
			MSR_RECOVERY_THROTTLE_TIME,
			MSR_RECOVERY_AS_SOURCE,
			MSR_RECOVERY_AS_TARGET,

			MSR_COMPLETION_SIZE,
			MSR_SEGMENT_COUNT,
			MSR_SEGMENT_SIZE,
			MSR_FILE_DESCRIPTOR_COUNT,
			MSR_FILE_DESCRIPTOR_LIMIT,
			MSR_FILE_SYSTEM_SIZE,
			MSR_PERCOLATE_COUNT,
			MSR_STORE_SIZE,
			MSR_STORE_THROTTLE_TIME,
			MSR_QUERY_CACHE_SIZE,
			MSR_FIELD_DATA_SIZE,
			MSR_FIELD_DATA_EVICTIONS,
	};

	/************************************** Variables for Configuration items **************************/

	private Boolean useFullUrlConfiguration = false;
	private String url;
	private int port;
	private String protocol;
	private String user;
	private String password;
	private long timeout;

	private final ObjectMapper mapper = new ObjectMapper();

	// for rate computations

    // Rate-Measures
    DerivedMeasure documentCount = new DerivedMeasure(TimeUnit.SECONDS);
    DerivedMeasure deletedCount = new DerivedMeasure(TimeUnit.SECONDS);

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#setup(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {

		Long tempPort = env.getConfigLong(ENV_CONFIG_PORT);

		protocol =  env.getConfigString(ENV_CONFIG_PROTOCOL);

		useFullUrlConfiguration =env.getConfigBoolean(ENV_CONFIG_USE_FULL_URL_CONFIGURATION);
		if(useFullUrlConfiguration) {
			url = env.getConfigString(ENV_CONFIG_URL);
			if (url == null || url.isEmpty()) {
				throw new IllegalArgumentException("Parameter <url> must not be empty");
			}
		}
		else {
			Host host = env.getHost();
			if (protocol == null || tempPort == null || host == null || host.getAddress() == null || host.getAddress().isEmpty())
				throw new IllegalArgumentException("Parameters <protocol>, <port> and the dynatrace native host list  must not be empty");
			port = tempPort.intValue();
			url = protocol + "://" + host.getAddress() + ":" + port;
		}
		// normalize URL
		url = StringUtils.removeEnd(url, "/");

		user = env.getConfigString(ENV_CONFIG_USER);
		if(user == null) {
			// to not fail in Apache HTTP Client
			user = "";
		}
		password = env.getConfigPassword(ENV_CONFIG_PASSWORD);
		if(env.getConfigString(ENV_CONFIG_TIMEOUT) != null) {
			timeout = env.getConfigLong(ENV_CONFIG_TIMEOUT);
		} else {
			timeout = 60_000;
		}
		if(timeout < 0 || timeout > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Timeout needs to be in range [0," + Integer.MAX_VALUE +"]");
		}

		return new Status(Status.StatusCode.Success);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#execute(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		log.info("Executing Elasticsearch Monitor for URL: " + url);

		try {
			// retrieve measures for cloud formation numbers in each state
			measureEnvironments(env);
		} catch (Throwable e) {
			// Our plugin functionality does not report Exceptions well...
			log.log(Level.WARNING, "Had throwable while running Elasticsearch Monitor with url " + url + ": " + ExceptionUtils.getStackTrace(e));
			throw new Exception(e);
		}

		return new Status(Status.StatusCode.Success);
	}

	private void measureEnvironments(MonitorEnvironment env) throws IOException {
		//final Collection<Instance> environments = getEnvironments();

		// walk all the different regions that were specified
		Measure nodeCount = new Measure();
		Measure dataNodeCount = new Measure();

		Measure activePrimaryShards = new Measure();
		Measure activeShardsPercent = new Measure();
		Measure activeShards = new Measure();
		Measure relocatingShards = new Measure();
		Measure initializingShards = new Measure();
		Measure unassignedShards = new Measure();
		Measure delayedUnassignedShards = new Measure();

		// JVM Memory
		Measure initHeap = new Measure("Node");
		Measure maxHeap = new Measure("Node");
		Measure initNonHeap = new Measure("Node");
		Measure maxNonHeap = new Measure("Node");
		Measure maxDirect = new Measure("Node");

		// Index/Shards
		Measure indexCount = new Measure();
		Measure shardsPerState = new Measure("State");

		// Stats
		Measure storeSizePerNode = new Measure("Node");
		Measure storeThrottleTimePerNode = new Measure("Node");
		Measure indexingThrottleTimePerNode = new Measure("Node");
		Measure indexingCurrentPerNode = new Measure("Node");
		Measure deleteCurrentPerNode = new Measure("Node");
		Measure queryCurrentPerNode = new Measure("Node");
		Measure fetchCurrentPerNode = new Measure("Node");
		Measure scrollCurrentPerNode = new Measure("Node");
		Measure queryCacheSizePerNode = new Measure("Node");
		Measure fieldDataSizePerNode = new Measure("Node");
		Measure percolateSizePerNode = new Measure("Node");
		Measure translogSizePerNode = new Measure("Node");
		Measure requestCacheSizePerNode = new Measure("Node");
		Measure recoveryThrottleTimePerNode = new Measure("Node");
		Measure recoveryAsSourcePerNode = new Measure("Node");
		Measure recoveryAsTargetPerNode = new Measure("Node");
        Measure fileDescLimitPerNode = new Measure("Node");

		Measure fieldDataSize = new Measure();
		Measure fieldDataEvictions = new Measure();
		Measure queryCachePerState = new Measure("State");
		Measure completionSize = new Measure();
		Measure segmentCount = new Measure();
		Measure segmentSizePerState = new Measure("State");
		Measure fileDescPerStat = new Measure("Stat");
		Measure fileSystemPerStat = new Measure("Stat");
		Measure percolatePerState = new Measure("State");

		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(user, password));

		RequestConfig reqConfig = RequestConfig.custom()
			    .setSocketTimeout((int)timeout)
			    .setConnectTimeout((int)timeout)
			    .setConnectionRequestTimeout((int)timeout)
			    .build();

		// configure the builder for HttpClients
		HttpClientBuilder builder = HttpClients.custom()
		        .setDefaultCredentialsProvider(credsProvider)
				.setDefaultRequestConfig(reqConfig);

		try (CloseableHttpClient client = builder.build()) {
			retrieveClusterHealth(client, nodeCount, dataNodeCount, activePrimaryShards, activeShardsPercent, activeShards,
					relocatingShards, initializingShards, unassignedShards, delayedUnassignedShards);

			retrieveNodeHealth(client, initHeap, maxHeap, initNonHeap, maxNonHeap, maxDirect);

			retrieveClusterState(client, indexCount, shardsPerState,
					fieldDataSize, fieldDataEvictions, queryCachePerState,
					completionSize, segmentCount, segmentSizePerState,
					fileDescPerStat, fileSystemPerStat, percolatePerState, documentCount, deletedCount);

			//retrieveIndexCounts(client, documentCountPerIndex, deletedCountPerIndex);

			retrieveNodeStats(client, storeSizePerNode, storeThrottleTimePerNode, indexingThrottleTimePerNode, indexingCurrentPerNode,
					deleteCurrentPerNode, queryCurrentPerNode, fetchCurrentPerNode, scrollCurrentPerNode, queryCacheSizePerNode,
					fieldDataSizePerNode, percolateSizePerNode, translogSizePerNode, requestCacheSizePerNode, recoveryThrottleTimePerNode,
					recoveryAsSourcePerNode, recoveryAsTargetPerNode, fileDescLimitPerNode);
		}

		// retrieve and set the measurements
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_NODE_COUNT, env, nodeCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DATA_NODE_COUNT, env, dataNodeCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_PRIMARY_SHARDS, env, activePrimaryShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_SHARDS, env, activeShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_SHARDS_PERCENT, env, activeShardsPercent);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RELOCATING_SHARDS, env, relocatingShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INITIALIZING_SHARDS, env, initializingShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_UNASSIGNED_SHARDS, env, unassignedShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELAYED_UNASSIGNED_SHARDS, env, delayedUnassignedShards);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_INIT_HEAP, env, initHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_HEAP, env, maxHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_INIT_NON_HEAP, env, initNonHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_NON_HEAP, env, maxNonHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_DIRECT, env, maxDirect);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEX_COUNT, env, indexCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SHARD_COUNT, env, shardsPerState);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DOCUMENT_COUNT, env, documentCount.getBaseMeasure());
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELETED_COUNT, env, deletedCount.getBaseMeasure());

		// this should not be reported as negative number if documents were removed,
		// e.g. when whole indexes are removed
		Measure docsPerSecond = documentCount.getDerivedMeasure();
		if(docsPerSecond.getValue() < 0) {
			docsPerSecond.setValue(0);
		}
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DOCUMENT_COUNT_PER_SECOND, env, docsPerSecond);

		// this should not be reported as negative number if documents were removed,
		// e.g. when whole indexes are removed
		Measure deletesPerSecond = deletedCount.getDerivedMeasure();
		if(deletesPerSecond.getValue() < 0) {
			deletesPerSecond.setValue(0);
		}
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELETED_COUNT_PER_SECOND, env, deletesPerSecond);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_STORE_SIZE, env, storeSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_STORE_THROTTLE_TIME, env, storeThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEXING_THROTTLE_TIME, env, indexingThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEXING_CURRENT, env, indexingCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELETE_CURRENT, env, deleteCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CURRENT, env, queryCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FETCH_CURRENT, env, fetchCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SCROLL_CURRENT, env, scrollCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CACHE_SIZE, env, queryCacheSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_SIZE, env, fieldDataSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_PERCOLATE_SIZE, env, percolateSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_TRANSLOG_SIZE, env, translogSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_REQUEST_CACHE_SIZE, env, requestCacheSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_THROTTLE_TIME, env, recoveryThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_AS_SOURCE, env, recoveryAsSourcePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_AS_TARGET, env, recoveryAsTargetPerNode);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_SIZE, env, fieldDataSize);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_EVICTIONS, env, fieldDataEvictions);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CACHE_SIZE, env, queryCachePerState);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_COMPLETION_SIZE, env, completionSize);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SEGMENT_COUNT, env, segmentCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SEGMENT_SIZE, env, segmentSizePerState);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FILE_DESCRIPTOR_COUNT, env, fileDescPerStat);
        writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FILE_DESCRIPTOR_LIMIT, env, fileDescLimitPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FILE_SYSTEM_SIZE, env, fileSystemPerStat);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_PERCOLATE_COUNT, env, percolatePerState);
	}

	/* does not work reliably and seems to be a costly operation
	private void retrieveIndexCounts(CloseableHttpClient client, Measure documentCount, Measure deletedCount) throws IOException {
		String json = simpleGet(client, url + "/_cat/indices");

		String [] indexes = json.split("[\\r\\n]");
		for(String index : indexes) {
			String[] items = index.split("\\s+");

			// green open visit-4               12 1 16039976    0  18.7gb   9.3gb
			String name = items[2];
			long docCount = Long.parseLong(items[5]);
			long delCount = Long.parseLong(items[6]);

			documentCount.addValueLong(docCount);
			documentCount.addDynamicMeasure(name, docCount);

			deletedCount.addValueLong(delCount);
			deletedCount.addDynamicMeasure(name, delCount);
		}
	}*/

	private void retrieveClusterState(CloseableHttpClient client,
                                      Measure indexCount, Measure shardsPerState,
                                      Measure fieldDataSize, Measure fieldDataEvictions, Measure queryCachePerState,
                                      Measure completionSize, Measure segmentCount, Measure segmentSizePerState,
                                      Measure fileDescPerStat, Measure fileSystemPerStat, Measure percolatePerState,
                                      DerivedMeasure documentCount, DerivedMeasure deletedCount) throws IOException {
		String json = simpleGet(client, url + "/_cluster/stats");
		JsonNode clusterStats = mapper.readTree(json);

		JsonNode index = clusterStats.get("indices");

		setValue(indexCount, index, "count");

		JsonNode shards = index.get("shards");
		if(shards != null) {
			setValue(shardsPerState, shards, "total");
			addDynamicMeasure(shardsPerState, "primary", shards, "primaries");
			addDynamicMeasure(shardsPerState, "replicationFactor", shards, "replication");
		}

		JsonNode docs = index.get("docs");
		if(docs != null) {
			setValue(documentCount, docs, "count");
			setValue(deletedCount, docs, "deleted");
		}

		/*JsonNode store = index.get("store");
		setValue(storeSize, store, "size_in_bytes");
		setValue(storeThrottleTime, store, "throttle_time_in_millis");*/

		JsonNode fielddata = index.get("fielddata");
		if(fielddata != null) {
			setValue(fieldDataSize, fielddata, "memory_size_in_bytes");
			setValue(fieldDataEvictions, fielddata, "evictions");
		}

		JsonNode query_cache = index.get("query_cache");
		if(query_cache != null) {
			setValue(queryCachePerState, query_cache, "memory_size_in_bytes");
			addDynamicMeasure(queryCachePerState, query_cache, "total_count");
			addDynamicMeasure(queryCachePerState, query_cache, "hit_count");
			addDynamicMeasure(queryCachePerState, query_cache, "miss_count");
			addDynamicMeasure(queryCachePerState, query_cache, "cache_size");
			addDynamicMeasure(queryCachePerState, query_cache, "cache_count");
			addDynamicMeasure(queryCachePerState, query_cache, "evictions");
		}

		JsonNode completion = index.get("completion");
		if(completion != null) {
			setValue(completionSize, completion, "size_in_bytes");
		}

		JsonNode segments = index.get("segments");
		if(segments != null) {
			setValue(segmentCount, segments, "count");

			addDynamicMeasure(segmentSizePerState, segments, "count");
			addDynamicMeasure(segmentSizePerState, segments, "memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "terms_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "stored_fields_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "term_vectors_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "norms_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "doc_values_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "index_writer_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "index_writer_max_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "version_map_memory_in_bytes");
			addDynamicMeasure(segmentSizePerState, segments, "fixed_bit_set_memory_in_bytes");
		}

		JsonNode percolate = index.get("percolate");
		if(percolate != null) {
			setValue(percolatePerState, percolate, "current");
			addDynamicMeasure(percolatePerState, percolate, "total");
			addDynamicMeasure(percolatePerState, percolate, "time_in_millis");
			addDynamicMeasure(percolatePerState, percolate, "current");
			addDynamicMeasure(percolatePerState, percolate, "memory_size_in_bytes");
			// not a double: addDynamicMeasure(percolatePerState, percolate, "memory_size");
			addDynamicMeasure(percolatePerState, percolate, "queries");
		}

		JsonNode nodes = clusterStats.get("nodes");
		if(nodes != null) {
			JsonNode process = nodes.get("process");
			if(process != null) {
				JsonNode fileDesc = process.get("open_file_descriptors");
				setValue(fileDescPerStat, fileDesc, "max");
				addDynamicMeasure(fileDescPerStat, fileDesc, "min");
				addDynamicMeasure(fileDescPerStat, fileDesc, "max");
				addDynamicMeasure(fileDescPerStat, fileDesc, "avg");
			}

			JsonNode fs = nodes.get("fs");
			// this was missing in tests sometimes
			if (fs != null) {
                setValue(fileSystemPerStat, fs, "free_in_bytes");
				addDynamicMeasure(fileSystemPerStat, fs, "total_in_bytes");
				addDynamicMeasure(fileSystemPerStat, fs, "free_in_bytes");
				addDynamicMeasure(fileSystemPerStat, fs, "available_in_bytes");
			}
		}
	}

    @SuppressWarnings("unused")
    private Map<String,String> retrieveNodeHealth(CloseableHttpClient client, Measure initHeap, Measure maxHeap,
			Measure initNonHeap, Measure maxNonHeap, Measure maxDirect) throws IOException {
		Map<String,String> nodeIdToName = new HashMap<>();

		String json = simpleGet(client, url + "/_nodes");
		JsonNode nodeHealth = mapper.readTree(json);

		if(nodeHealth.get("nodes") != null) {
			Iterator<Map.Entry<String, JsonNode>> nodes = nodeHealth.get("nodes").fields();
			while (nodes.hasNext()) {
				Map.Entry<String, JsonNode> node = nodes.next();
				JsonNode name = node.getValue().get("name");
				final String nodeName;
				if (name == null) {
					nodeName = "unknown-node";
				} else {
					nodeName = checkNotNull(name.asText());
				}
				nodeIdToName.put(node.getKey(), nodeName);

				JsonNode jvm = node.getValue().get("jvm");
				if (jvm != null) {
					JsonNode mem = jvm.get("mem");
					if (mem != null) {
						addValueLong(initHeap, mem, "heap_init_in_bytes");
						addDynamicMeasureLong(initHeap, nodeName, mem, "heap_init_in_bytes");

						addValueLong(maxHeap, mem, "heap_max_in_bytes");
						addDynamicMeasureLong(maxHeap, nodeName, mem, "heap_max_in_bytes");

						addValueLong(initNonHeap, mem, "non_heap_init_in_bytes");
						addDynamicMeasureLong(initNonHeap, nodeName, mem, "non_heap_init_in_bytes");

						addValueLong(maxNonHeap, mem, "non_heap_max_in_bytes");
						addDynamicMeasureLong(maxNonHeap, nodeName, mem, "non_heap_max_in_bytes");

						addValueLong(maxDirect, mem, "direct_max_in_bytes");
						addDynamicMeasureLong(maxDirect, nodeName, mem, "direct_max_in_bytes");
					}
				}
			}
		}

		return nodeIdToName;
	}

    private void retrieveNodeStats(CloseableHttpClient client, Measure storeSizePerNode, Measure storeThrottleTimePerNode,
			Measure indexingThrottleTimePerNode, Measure indexingCurrentPerNode, Measure deleteCurrentPerNode,
			Measure queryCurrentPerNode, Measure fetchCurrentPerNode, Measure scrollCurrentPerNode,
			Measure queryCacheSizePerNode, Measure fieldDataSizePerNode, Measure percolateSizePerNode,
			Measure translogSizePerNode, Measure requestCacheSizePerNode, Measure recoveryThrottleTimePerNode,
			Measure recoveryAsSourcePerNode, Measure recoveryAsTargetPerNode, Measure fileDescLimitPerNode) throws IOException {
		String json = simpleGet(client, url + "/_nodes/stats");
		JsonNode nodeHealth = mapper.readTree(json);

		if(nodeHealth.get("nodes") != null) {
			Iterator<Map.Entry<String, JsonNode>> nodes = nodeHealth.get("nodes").fields();
			while (nodes.hasNext()) {
				Map.Entry<String, JsonNode> node = nodes.next();
				JsonNode name = node.getValue().get("name");
				final String nodeName;
				if (name == null) {
					nodeName = "unknown-node";
				} else {
					nodeName = checkNotNull(name.asText());
				}

				JsonNode process = node.getValue().get("process");
				if (process != null) {
					addValueLong(fileDescLimitPerNode, process, "max_file_descriptors");
					addDynamicMeasureLong(fileDescLimitPerNode, nodeName, process, "max_file_descriptors");
				}

				JsonNode indices = node.getValue().get("indices");
				if (indices != null) {
					JsonNode store = indices.get("store");
					if (store != null) {
						addValueLong(storeSizePerNode, store, "size_in_bytes");
						addDynamicMeasureLong(storeSizePerNode, nodeName, store, "size_in_bytes");

						addValueLong(storeThrottleTimePerNode, store, "throttle_time_in_millis");
						addDynamicMeasureLong(storeThrottleTimePerNode, nodeName, store, "throttle_time_in_millis");
					}

					JsonNode indexing = indices.get("indexing");
					if (indexing != null) {
						addValueLong(indexingThrottleTimePerNode, indexing, "throttle_time_in_millis");
						addDynamicMeasureLong(indexingThrottleTimePerNode, nodeName, indexing, "throttle_time_in_millis");

						addValueLong(indexingCurrentPerNode, indexing, "index_current");
						addDynamicMeasureLong(indexingCurrentPerNode, nodeName, indexing, "index_current");

						addValueLong(deleteCurrentPerNode, indexing, "delete_current");
						addDynamicMeasureLong(deleteCurrentPerNode, nodeName, indexing, "delete_current");
					}

					JsonNode search = indices.get("search");
					if (search != null) {
						addValueLong(queryCurrentPerNode, search, "query_current");
						addDynamicMeasureLong(queryCurrentPerNode, nodeName, search, "query_current");

						addValueLong(fetchCurrentPerNode, search, "fetch_current");
						addDynamicMeasureLong(fetchCurrentPerNode, nodeName, search, "fetch_current");

						addValueLong(scrollCurrentPerNode, search, "scroll_current");
						addDynamicMeasureLong(scrollCurrentPerNode, nodeName, search, "scroll_current");
					}

					JsonNode queryCache = indices.get("query_cache");
					if (queryCache != null) {
						addValueLong(queryCacheSizePerNode, queryCache, "memory_size_in_bytes");
						addDynamicMeasureLong(queryCacheSizePerNode, nodeName, queryCache, "memory_size_in_bytes");
					}

					JsonNode fieldData = indices.get("fielddata");
					if (fieldData != null) {
						addValueLong(fieldDataSizePerNode, fieldData, "memory_size_in_bytes");
						addDynamicMeasureLong(fieldDataSizePerNode, nodeName, fieldData, "memory_size_in_bytes");
					}

					JsonNode percolate = indices.get("percolate");
					if (percolate != null) {
						addValueLong(percolateSizePerNode, percolate, "memory_size_in_bytes");
						addDynamicMeasureLong(percolateSizePerNode, nodeName, percolate, "memory_size_in_bytes");
					}

					JsonNode translog = indices.get("translog");
					if (translog != null) {
						addValueLong(translogSizePerNode, translog, "size_in_bytes");
						addDynamicMeasureLong(translogSizePerNode, nodeName, translog, "size_in_bytes");
					}

					JsonNode requestCache = indices.get("request_cache");
					if (requestCache != null) {
						addValueLong(requestCacheSizePerNode, requestCache, "memory_size_in_bytes");
						addDynamicMeasureLong(requestCacheSizePerNode, nodeName, requestCache, "memory_size_in_bytes");
					}

					JsonNode recovery = indices.get("recovery");
					if (recovery != null) {
						addValueLong(recoveryThrottleTimePerNode, recovery, "throttle_time_in_millis");
						addDynamicMeasureLong(recoveryThrottleTimePerNode, nodeName, recovery, "throttle_time_in_millis");

						addValueLong(recoveryAsSourcePerNode, recovery, "current_as_source");
						addDynamicMeasureLong(recoveryAsSourcePerNode, nodeName, recovery, "current_as_source");

						addValueLong(recoveryAsTargetPerNode, recovery, "current_as_target");
						addDynamicMeasureLong(recoveryAsTargetPerNode, nodeName, recovery, "current_as_target");
					}
				}
			}
		}
	}

    private void retrieveClusterHealth(CloseableHttpClient client, Measure nodeCount, Measure dataNodeCount, Measure activePrimaryShards, Measure activeShardsPercent, Measure activeShards,
			Measure relocatingShards, Measure initializingShards, Measure unassignedShards, Measure delayedUnassignedShards) throws IOException {
		String json = simpleGet(client, url + "/_cluster/health");
		JsonNode clusterHealth = mapper.readTree(json);

		setValueLong(nodeCount, clusterHealth, "number_of_nodes");
		setValueLong(dataNodeCount, clusterHealth, "number_of_data_nodes");
		setValueLong(activePrimaryShards, clusterHealth, "active_primary_shards");
		setValue(activeShardsPercent, clusterHealth, "active_shards_percent_as_number");
		setValueLong(activeShards, clusterHealth, "active_shards");
		setValueLong(relocatingShards, clusterHealth, "relocating_shards");
		setValueLong(initializingShards, clusterHealth, "initializing_shards");
		setValueLong(unassignedShards, clusterHealth, "unassigned_shards");
		setValueLong(delayedUnassignedShards, clusterHealth, "delayed_unassigned_shards");

		/* Not yet read:
				"number_of_pending_tasks": 0,
				"number_of_in_flight_fetch": 0

		// only in 2.0.0 and above:
				"task_max_waiting_in_queue_millis": 0,
		*/
	}

	/*private void retrievePSGInformation(Measure psgCount, Measure psgCountByEnvironment,
			Measure psgCountByOs, Measure psgCountByVersion, Measure psgCountByInstallerVersion,
			Instance environment, DebugUIAccess download) throws IOException {
		if(log.isLoggable(Level.FINE)) {
			log.fine("Retrieving private security gateway information for environment: " + environment);
		}
		List<Base> psgs = download.getSecurityGateways(environment);
		for(Base base : psgs) {
			String privateCollector = base.get("privateCollector");
			if(!privateCollector.equalsIgnoreCase("true")) {
				continue;
			}

			psgCount.addValueLong(1);

			psgCountByEnvironment.addValueLong(1);
			psgCountByEnvironment.addDynamicMeasure(environment.name(), 1);

			psgCountByOs.addValueLong(1);
			psgCountByOs.addDynamicMeasure(base.get("osInfo"), 1);

			psgCountByVersion.addValueLong(1);
			psgCountByVersion.addDynamicMeasure(base.get("buildVersion"), 1);

			psgCountByInstallerVersion.addValueLong(1);
			psgCountByInstallerVersion.addDynamicMeasure(base.get("productVersion"), 1);
		}
	}
*/

    private void setValue(Measure measure, JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if(node != null) {
            measure.setValue(node.asDouble());
        }
    }

    private void setValue(DerivedMeasure measure, JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if(node != null) {
            measure.setValue(node.asDouble(), System.currentTimeMillis());
        }
    }

    private void setValueLong(Measure measure, JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if(node != null) {
            measure.setValue(node.asLong());
        }
    }

    private void addValueLong(Measure measure, JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if(node != null) {
            measure.addValue(node.asLong());
        }
    }

    private void addDynamicMeasure(Measure measure, String jsonMeasure, JsonNode jsonNode, String key) {
        JsonNode value = jsonNode.get(jsonMeasure);
        if(value != null) {
            measure.addDynamicMeasure(key, value.asDouble());
        }
    }

    private void addDynamicMeasure(Measure measure, JsonNode jsonNode, String jsonMeasure) {
        JsonNode value = jsonNode.get(jsonMeasure);
        if(value != null) {
            measure.addDynamicMeasure(jsonMeasure, value.asDouble());
        }
    }

    private void addDynamicMeasureLong(Measure measure, String dynamicMeasureName, JsonNode jsonNode, String jsonMeasure) {
        JsonNode value = jsonNode.get(jsonMeasure);
        if(value != null) {
            measure.addDynamicMeasure(dynamicMeasureName, value.asLong());
        }
    }

	protected void writeMeasure(String group, String name, MonitorEnvironment env, Measure value) {
		Collection<MonitorMeasure> measures = env.getMonitorMeasures(group, name);
		if (measures != null) {
			if (log.isLoggable(Level.INFO)) {
				log.info("Setting measure '" + name + "' to value " + value.getValue() +
						(value.getDynamicMeasureName() == null ? "" :
							", dynamic: " + value.getDynamicMeasureName() + ": " + value.getDynamicMeasures()) +
						", measures: " + measures);
			}
			for (MonitorMeasure measure : measures) {
				measure.setValue(value.getValue());

				if(value.getDynamicMeasures().size() > 0) {
					// TODO: somehow we need to write this once more, why is this necessary?!?
					Measure copyMeasure = new Measure();
					copyMeasure.setValue(value.getValue());
					writeMeasure(group, name, env, copyMeasure);

				    // for this subscribed measure we want to create a dynamic measure
					for(Map.Entry<String, Double> dynamic : value.getDynamicMeasures().entrySet()) {
						Preconditions.checkNotNull(value.getDynamicMeasureName(), "Had null as dynamic measure name for measure %s and dynamic measures %s", measure, value.getDynamicMeasures());
						Preconditions.checkNotNull(dynamic.getKey(), "Had null as dynamic measure key for measure %s and dynamic measures %s", measure, value.getDynamicMeasures());

						MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, value.getDynamicMeasureName(), dynamic.getKey());
						dynamicMeasure.setValue(dynamic.getValue());
					}
				}
			}
		} else {
			log.warning("Could not find measure " + name + "@" + group + ", tried to report value: " + value);
		}
	}

	public String simpleGet(CloseableHttpClient httpClient, String url) throws IOException {
		// Required to avoid two requests instead of one: See http://stackoverflow.com/questions/20914311/httpclientbuilder-basic-auth
		AuthCache authCache = new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();

		// Generate BASIC scheme object and add it to the local auth cache
		URL cacheUrl = new URL(url);
		HttpHost targetHost = new HttpHost(cacheUrl.getHost(), cacheUrl.getPort(), cacheUrl.getProtocol());
		authCache.put(targetHost, basicAuth);

		// Add AuthCache to the execution context
		HttpClientContext context = HttpClientContext.create();
		//context.setCredentialsProvider(credsProvider);
		context.setAuthCache(authCache);

		final HttpGet httpGet = new HttpGet(url);
		try (CloseableHttpResponse response = httpClient.execute(targetHost, httpGet, context)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if(statusCode != 200) {
				String msg = "Had HTTP StatusCode " + statusCode + " for request: " + url + ", response: " + response.getStatusLine().getReasonPhrase();
				log.warning(msg);

				throw new IOException(msg);
			}
		    HttpEntity entity = response.getEntity();

		    try {
		    	return IOUtils.toString(entity.getContent(), "UTF-8");
		    } finally {
			    // ensure all content is taken out to free resources
			    EntityUtils.consume(entity);
		    }
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#teardown(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		// nothing to do here
	}

}
