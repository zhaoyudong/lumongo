package org.lumongo.server.indexing;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.LumongoDirectoryTaxonomyWriter;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LumongoIndexWriter;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.bson.BSON;
import org.bson.BSONObject;
import org.lumongo.LumongoConstants;
import org.lumongo.cluster.message.Lumongo.AssociatedDocument;
import org.lumongo.cluster.message.Lumongo.DeleteRequest;
import org.lumongo.cluster.message.Lumongo.FetchType;
import org.lumongo.cluster.message.Lumongo.FieldSort;
import org.lumongo.cluster.message.Lumongo.GetFieldNamesResponse;
import org.lumongo.cluster.message.Lumongo.GetNumberOfDocsResponse;
import org.lumongo.cluster.message.Lumongo.GetTermsRequest;
import org.lumongo.cluster.message.Lumongo.GetTermsResponse;
import org.lumongo.cluster.message.Lumongo.IndexSegmentResponse;
import org.lumongo.cluster.message.Lumongo.IndexSettings;
import org.lumongo.cluster.message.Lumongo.LMAnalyzer;
import org.lumongo.cluster.message.Lumongo.LastIndexResult;
import org.lumongo.cluster.message.Lumongo.LastResult;
import org.lumongo.cluster.message.Lumongo.QueryRequest;
import org.lumongo.cluster.message.Lumongo.ResultDocument;
import org.lumongo.cluster.message.Lumongo.ScoredResult;
import org.lumongo.cluster.message.Lumongo.SegmentCountResponse;
import org.lumongo.cluster.message.Lumongo.SegmentResponse;
import org.lumongo.cluster.message.Lumongo.SortRequest;
import org.lumongo.cluster.message.Lumongo.StoreRequest;
import org.lumongo.cluster.message.Lumongo.Term;
import org.lumongo.server.config.ClusterConfig;
import org.lumongo.server.config.IndexConfig;
import org.lumongo.server.config.MongoConfig;
import org.lumongo.server.exceptions.InvalidIndexConfig;
import org.lumongo.server.exceptions.SegmentDoesNotExist;
import org.lumongo.server.hazelcast.HazelcastManager;
import org.lumongo.server.hazelcast.UpdateSegmentsTask;
import org.lumongo.server.searching.QueryWithFilters;
import org.lumongo.storage.constants.MongoConstants;
import org.lumongo.storage.lucene.DistributedDirectory;
import org.lumongo.storage.lucene.MongoDirectory;
import org.lumongo.storage.rawfiles.DocumentStorage;
import org.lumongo.storage.rawfiles.MongoDocumentStorage;
import org.lumongo.util.LockHandler;
import org.lumongo.util.LumongoThreadFactory;
import org.lumongo.util.SegmentUtil;

import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.ILock;
import com.hazelcast.core.Member;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

public class LumongoIndex {
	public static final String STORAGE_DB_SUFFIX = "_rs";
	
	private static final String RESULT_STORAGE_COLLECTION = "resultStorage";
	private static final String FACETS_SUFFIX = "facets";
	private final static Logger log = Logger.getLogger(LumongoIndex.class);
	private static final String SETTINGS_ID = "settings";
	public static final String CONFIG_SUFFIX = "_config";
	
	private final IndexConfig indexConfig;
	private final MongoConfig mongoConfig;
	private final ClusterConfig clusterConfig;
	
	private final MongoClient mongo;
	private MongoClient storageMongoClient;
	
	private final GenericObjectPool<LumongoQueryParser> parsers;
	
	private Map<Member, Set<Integer>> memberToSegmentMap;
	private Map<Integer, Member> segmentToMemberMap;
	
	private final ConcurrentHashMap<Integer, LumongoSegment> segmentMap;
	private final ConcurrentHashMap<Integer, ILock> hazelLockMap;
	
	private final ReadWriteLock indexLock;
	
	private Timer commitTimer;
	
	private final ExecutorService segmentPool;
	
	private final int numberOfSegments;
	private final String indexName;
	
	private TimerTask commitTask;
	
	private final HazelcastManager hazelcastManager;
	
	private final DocumentStorage documentStorage;
	
	private LumongoAnalyzerFactory lumongoAnalyzerFactory;
	
	private LockHandler documentLockHandler;
	
	private LumongoIndex(HazelcastManager hazelcastManger, MongoConfig mongoConfig, ClusterConfig clusterConfig, IndexConfig indexConfig)
					throws UnknownHostException, MongoException {
		
		this.documentLockHandler = new LockHandler();
		
		this.hazelcastManager = hazelcastManger;
		
		this.mongoConfig = mongoConfig;
		this.clusterConfig = clusterConfig;
		
		this.indexConfig = indexConfig;
		this.indexName = indexConfig.getIndexName();
		this.numberOfSegments = indexConfig.getNumberOfSegments();
		
		this.mongo = new MongoClient(mongoConfig.getMongoHost(), mongoConfig.getMongoPort());
		
		this.storageMongoClient = new MongoClient(mongoConfig.getMongoHost(), mongoConfig.getMongoPort());
		
		String rawStorageDb = mongoConfig.getDatabaseName() + "_" + indexName + STORAGE_DB_SUFFIX;
		
		this.documentStorage = new MongoDocumentStorage(storageMongoClient, indexName, rawStorageDb, RESULT_STORAGE_COLLECTION, clusterConfig.isSharded());
		
		this.segmentPool = Executors.newCachedThreadPool(new LumongoThreadFactory(indexName + "-segments"));
		
		this.parsers = new GenericObjectPool<LumongoQueryParser>(new BasePoolableObjectFactory<LumongoQueryParser>() {
			
			@Override
			public LumongoQueryParser makeObject() throws Exception {
				return new LumongoQueryParser(lumongoAnalyzerFactory.getAnalyzer(), LumongoIndex.this.indexConfig);
			}
			
		});
		
		this.indexLock = new ReentrantReadWriteLock(true);
		this.segmentMap = new ConcurrentHashMap<Integer, LumongoSegment>();
		this.hazelLockMap = new ConcurrentHashMap<Integer, ILock>();
		
		commitTimer = new Timer(indexName + "-CommitTimer", true);
		
		commitTask = new TimerTask() {
			
			@Override
			public void run() {
				if (LumongoIndex.this.indexConfig.getIdleTimeWithoutCommit() != 0) {
					doCommit(false);
				}
				
			}
			
		};
		
		commitTimer.scheduleAtFixedRate(commitTask, 1000, 1000);
		
		this.lumongoAnalyzerFactory = new LumongoAnalyzerFactory(indexConfig);
		
	}
	
	public void updateIndexSettings(IndexSettings request) {
		indexLock.writeLock().lock();
		try {
			log.info("Updating index settings for <" + indexName + ">: " + request);
			indexConfig.configure(request);
			storeIndexSettings();
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	public LMAnalyzer getLMAnalyzer(String fieldName) {
		return indexConfig.getAnalyzer(fieldName);
	}
	
	private void doCommit(boolean force) {
		indexLock.readLock().lock();
		try {
			Collection<LumongoSegment> segments = segmentMap.values();
			for (LumongoSegment segment : segments) {
				try {
					if (force) {
						segment.forceCommit();
					}
					else {
						segment.doCommit();
					}
				}
				catch (Exception e) {
					log.error("Failed to flushing segment <" + segment.getSegmentNumber() + "> for index <" + indexName + ">: " + e.getClass().getSimpleName()
									+ ": ", e);
				}
			}
		}
		finally {
			indexLock.readLock().unlock();
		}
		
	}
	
	public void updateSegmentMap(Map<Member, Set<Integer>> newMemberToSegmentMap) {
		indexLock.writeLock().lock();
		try {
			log.info("Updating segments map");
			
			this.memberToSegmentMap = newMemberToSegmentMap;
			this.segmentToMemberMap = new HashMap<Integer, Member>();
			
			for (Member m : memberToSegmentMap.keySet()) {
				for (int i : memberToSegmentMap.get(m)) {
					segmentToMemberMap.put(i, m);
				}
			}
			
			Member self = hazelcastManager.getSelf();
			
			Set<Integer> newSegments = memberToSegmentMap.get(self);
			
			log.info("Settings segments for this node <" + self + "> to <" + newSegments + ">");
			
			for (Integer segmentNumber : segmentMap.keySet()) {
				if (!newSegments.contains(segmentNumber)) {
					try {
						unloadSegment(segmentNumber);
					}
					catch (Exception e) {
						log.error("Error unloading segment <" + segmentNumber + "> for index <" + indexName + ">");
						log.error(e.getClass().getSimpleName() + ": ", e);
					}
				}
			}
			
			for (Integer segmentNumber : newSegments) {
				if (!segmentMap.containsKey(segmentNumber)) {
					try {
						loadSegment(segmentNumber);
					}
					catch (Exception e) {
						log.error("Error loading segment <" + segmentNumber + "> for index <" + indexName + ">");
						log.error(e.getClass().getSimpleName() + ": ", e);
					}
				}
			}
			
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	public void loadAllSegments() throws Exception {
		indexLock.writeLock().lock();
		try {
			Member self = hazelcastManager.getSelf();
			this.memberToSegmentMap = new HashMap<Member, Set<Integer>>();
			this.memberToSegmentMap.put(self, new HashSet<Integer>());
			for (int segmentNumber = 0; segmentNumber < numberOfSegments; segmentNumber++) {
				loadSegment(segmentNumber);
				this.memberToSegmentMap.get(self).add(segmentNumber);
			}
			
			this.segmentToMemberMap = new HashMap<Integer, Member>();
			
			for (Member m : memberToSegmentMap.keySet()) {
				for (int i : memberToSegmentMap.get(m)) {
					segmentToMemberMap.put(i, m);
				}
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	public void unload() throws CorruptIndexException, IOException {
		indexLock.writeLock().lock();
		try {
			log.info("Canceling timers for <" + indexName + ">");
			commitTask.cancel();
			commitTimer.cancel();
			log.info("Commiting <" + indexName + ">");
			doCommit(true);
			
			log.info("Shutting segment pool for <" + indexName + ">");
			segmentPool.shutdownNow();
			
			for (Integer segmentNumber : segmentMap.keySet()) {
				unloadSegment(segmentNumber);
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	public FacetsConfig getFacetsConfig() {
		//only need to be done once but no harm
		FacetsConfig.DEFAULT_DIM_CONFIG.hierarchical = true;
		FacetsConfig.DEFAULT_DIM_CONFIG.multiValued = true;
		return new FacetsConfig() {
			@Override
			public synchronized DimConfig getDimConfig(String dimName) {
				DimConfig dc = new DimConfig();
				dc.multiValued = true;
				dc.hierarchical = true;
				return dc;
			}
		};
	}
	
	private void loadSegment(int segmentNumber) throws Exception {
		indexLock.writeLock().lock();
		try {
			if (!segmentMap.containsKey(segmentNumber)) {
				String lockName = indexName + "-" + segmentNumber;
				ILock hzLock = hazelcastManager.getLock(lockName);
				hazelLockMap.put(segmentNumber, hzLock);
				log.info("Waiting for lock for index <" + indexName + "> segment <" + segmentNumber + ">");
				hzLock.lock();
				log.info("Obtained lock for index <" + indexName + "> segment <" + segmentNumber + ">");
				
				String indexSegmentDbName = getIndexSegmentDbName(segmentNumber);
				String indexSegmentCollectionName = getIndexSegmentCollectionName(segmentNumber);
				
				MongoDirectory mongoDirectory = new MongoDirectory(mongo, indexSegmentDbName, indexSegmentCollectionName, clusterConfig.isSharded(),
								indexConfig.isBlockCompression(), clusterConfig.getIndexBlockSize());
				DistributedDirectory dd = new DistributedDirectory(mongoDirectory);
				
				IndexWriterConfig config = new IndexWriterConfig(Version.LATEST, null);
				//use flush interval to flush
				config.setMaxBufferedDocs(Integer.MAX_VALUE);
				config.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
				
				LumongoIndexWriter indexWriter = new LumongoIndexWriter(dd, config);
				
				LumongoDirectoryTaxonomyWriter taxonomyWriter = null;
				FacetsConfig facetsConfig = null;
				
				MongoDirectory mongoFacetDirectory = new MongoDirectory(mongo, indexSegmentDbName, indexSegmentCollectionName + "_" + FACETS_SUFFIX,
								clusterConfig.isSharded(), indexConfig.isBlockCompression(), clusterConfig.getIndexBlockSize());
				DistributedDirectory ddFacet = new DistributedDirectory(mongoFacetDirectory);
				taxonomyWriter = new LumongoDirectoryTaxonomyWriter(ddFacet);
				facetsConfig = getFacetsConfig();
				
				LumongoSegment s = new LumongoSegment(segmentNumber, indexWriter, taxonomyWriter, indexConfig, facetsConfig,
								lumongoAnalyzerFactory.getAnalyzer());
				segmentMap.put(segmentNumber, s);
				
				log.info("Loaded segment <" + segmentNumber + "> for index <" + indexName + ">");
				log.info("Current segments <" + (new TreeSet<Integer>(segmentMap.keySet())) + "> for index <" + indexName + ">");
				
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	private String getIndexSegmentCollectionName(int segmentNumber) {
		String indexSegmentCollectionName = indexName + "_" + segmentNumber;
		return indexSegmentCollectionName;
	}
	
	private String getIndexSegmentDbName(int segmentNumber) {
		String indexSegmentDbName;
		
		boolean databasePerIndexSegment = false;
		if (databasePerIndexSegment) {
			indexSegmentDbName = mongoConfig.getDatabaseName() + "_" + indexName + "_" + segmentNumber;
		}
		else {
			indexSegmentDbName = mongoConfig.getDatabaseName() + "_" + indexName;
		}
		return indexSegmentDbName;
	}
	
	public void unloadSegment(int segmentNumber) throws CorruptIndexException, IOException {
		indexLock.writeLock().lock();
		try {
			ILock hzLock = hazelLockMap.get(segmentNumber);
			try {
				if (segmentMap.containsKey(segmentNumber)) {
					LumongoSegment s = segmentMap.remove(segmentNumber);
					if (s != null) {
						log.info("Commiting and closing segment <" + segmentNumber + "> for index <" + indexName + ">");
						s.close();
						log.info("Removed segment <" + segmentNumber + "> for index <" + indexName + ">");
						log.info("Current segments <" + (new TreeSet<Integer>(segmentMap.keySet())) + "> for index <" + indexName + ">");
					}
				}
				
			}
			finally {
				try {
					hzLock.forceUnlock();
					log.info("Unlocked lock for index <" + indexName + "> segment <" + segmentNumber + ">");
				}
				catch (Exception e) {
					log.error("Failed to unlock <" + segmentNumber + ">: ", e);
				}
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	/**
	 * called on older cluster node when a new member is added
	 *
	 * @param currentMembers
	 *            - current cluster members
	 * @param memberAdded
	 *            - member that is being added
	 */
	public void handleServerAdded(Set<Member> currentMembers, Member memberAdded) {
		indexLock.writeLock().lock();
		try {
			forceBalance(currentMembers);
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	public void forceBalance(Set<Member> currentMembers) {
		indexLock.writeLock().lock();
		try {
			mapSanityCheck(currentMembers);
			balance(currentMembers);
			
			IExecutorService executorService = hazelcastManager.getExecutorService();
			
			List<Future<Void>> results = new ArrayList<Future<Void>>();
			
			for (Member m : currentMembers) {
				
				try {
					UpdateSegmentsTask ust = new UpdateSegmentsTask(m.getSocketAddress().getPort(), indexName, memberToSegmentMap);
					if (!m.localMember()) {
						Future<Void> dt = executorService.submitToMember(ust, m);
						results.add(dt);
					}
				}
				catch (Exception e) {
					log.error(e.getClass().getSimpleName() + ": ", e);
				}
				
			}
			
			try {
				UpdateSegmentsTask ust = new UpdateSegmentsTask(hazelcastManager.getHazelcastPort(), indexName, memberToSegmentMap);
				ust.call();
			}
			catch (Exception e) {
				log.error(e.getClass().getSimpleName() + ": ", e);
			}
			for (Future<Void> result : results) {
				try {
					result.get();
				}
				catch (Exception e) {
					log.error(e.getClass().getSimpleName() + ": ", e);
				}
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	/**
	 * Called on older cluster node when member is removed
	 *
	 * @param currentMembers
	 *            - current cluster members
	 * @param memberRemoved
	 *            - member that is being removed
	 */
	public void handleServerRemoved(Set<Member> currentMembers, Member memberRemoved) {
		indexLock.writeLock().lock();
		try {
			Set<Integer> segmentsToRedist = memberToSegmentMap.remove(memberRemoved);
			if (segmentsToRedist != null) {
				Member first = currentMembers.iterator().next();
				memberToSegmentMap.get(first).addAll(segmentsToRedist);
			}
			
			forceBalance(currentMembers);
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	private void balance(Set<Member> currentMembers) {
		indexLock.writeLock().lock();
		try {
			boolean balanced = false;
			do {
				int minSegmentsForMember = Integer.MAX_VALUE;
				int maxSegmentsForMember = Integer.MIN_VALUE;
				Member minMember = null;
				Member maxMember = null;
				for (Member m : currentMembers) {
					int segmentsForMemberCount = 0;
					Set<Integer> segmentsForMember = memberToSegmentMap.get(m);
					if (segmentsForMember != null) {
						segmentsForMemberCount = segmentsForMember.size();
					}
					if (segmentsForMemberCount < minSegmentsForMember) {
						minSegmentsForMember = segmentsForMemberCount;
						minMember = m;
					}
					if (segmentsForMemberCount > maxSegmentsForMember) {
						maxSegmentsForMember = segmentsForMemberCount;
						maxMember = m;
					}
				}
				
				if ((maxSegmentsForMember - minSegmentsForMember) > 1) {
					int valueToMove = memberToSegmentMap.get(maxMember).iterator().next();
					
					log.info("Moving segment <" + valueToMove + "> from <" + maxMember + "> to <" + minMember + "> of Index <" + indexName + ">");
					memberToSegmentMap.get(maxMember).remove(valueToMove);
					
					if (!memberToSegmentMap.containsKey(minMember)) {
						memberToSegmentMap.put(minMember, new HashSet<Integer>());
					}
					
					memberToSegmentMap.get(minMember).add(valueToMove);
				}
				else {
					balanced = true;
				}
				
			}
			while (!balanced);
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	private void mapSanityCheck(Set<Member> currentMembers) {
		indexLock.writeLock().lock();
		try {
			// add all segments to a set
			Set<Integer> allSegments = new HashSet<Integer>();
			for (int segment = 0; segment < indexConfig.getNumberOfSegments(); segment++) {
				allSegments.add(segment);
			}
			
			// ensure all members are in the map and contain an empty set
			for (Member m : currentMembers) {
				if (!memberToSegmentMap.containsKey(m)) {
					memberToSegmentMap.put(m, new HashSet<Integer>());
				}
				if (memberToSegmentMap.get(m) == null) {
					memberToSegmentMap.put(m, new HashSet<Integer>());
				}
			}
			
			// get all members of the map
			Set<Member> mapMembers = memberToSegmentMap.keySet();
			for (Member m : mapMembers) {
				
				// get current segments
				Set<Integer> segments = memberToSegmentMap.get(m);
				
				Set<Integer> invalidSegments = new HashSet<Integer>();
				for (int segment : segments) {
					// check if valid segment
					if (!allSegments.contains(segment)) {
						if ((segment < 0) || (segment >= indexConfig.getNumberOfSegments())) {
							log.error("Segment <" + segment + "> should not exist for cluster");
						}
						else {
							log.error("Segment <" + segment + "> is duplicated in node <" + m + ">");
						}
						invalidSegments.add(segment);
						
					}
				}
				// remove any invalid segments for the cluster
				segments.removeAll(invalidSegments);
				// remove from all segments to keep track of segments already used
				allSegments.removeAll(segments);
			}
			
			// adds any segments that are missing back to the first node
			if (!allSegments.isEmpty()) {
				log.error("Segments <" + allSegments + "> are missing from the cluster. Adding back in.");
				memberToSegmentMap.values().iterator().next().addAll(allSegments);
			}
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	public LumongoSegment findSegmentFromUniqueId(String uniqueId) throws SegmentDoesNotExist {
		indexLock.readLock().lock();
		try {
			int segmentNumber = getSegmentNumberForUniqueId(uniqueId);
			LumongoSegment s = segmentMap.get(segmentNumber);
			if (s == null) {
				throw new SegmentDoesNotExist(indexName, segmentNumber);
			}
			return s;
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public Member findMember(String uniqueId) {
		indexLock.readLock().lock();
		try {
			int segmentNumber = getSegmentNumberForUniqueId(uniqueId);
			Member owner = segmentToMemberMap.get(segmentNumber);
			return owner;
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public Map<Integer, Member> getSegmentToMemberMap() {
		return new HashMap<Integer, Member>(segmentToMemberMap);
	}
	
	private int getSegmentNumberForUniqueId(String uniqueId) {
		int numSegments = indexConfig.getNumberOfSegments();
		return SegmentUtil.findSegmentForUniqueId(uniqueId, numSegments);
	}
	
	public void deleteIndex() throws Exception {
		
		{
			DB db = mongo.getDB(mongoConfig.getDatabaseName());
			DBCollection dbCollection = db.getCollection(indexName + CONFIG_SUFFIX);
			dbCollection.drop();
		}
		for (int i = 0; i < numberOfSegments; i++) {
			
			String dbName = getIndexSegmentDbName(i);
			String collectionName = getIndexSegmentCollectionName(i);
			MongoDirectory.dropIndex(mongo, dbName, collectionName);
			MongoDirectory.dropIndex(mongo, dbName, collectionName + "_" + FACETS_SUFFIX);
			
			documentStorage.drop();
		}
		
		for (int i = 0; i < numberOfSegments; i++) {
			String dbName = getIndexSegmentDbName(i);
			DB db = mongo.getDB(dbName);
			db.dropDatabase();
		}
		
	}
	
	public void storeInternal(StoreRequest storeRequest) throws Exception {
		indexLock.readLock().lock();
		
		try {
			
			long timestamp = hazelcastManager.getClusterTime();
			
			String uniqueId = storeRequest.getUniqueId();
			ReadWriteLock documentLock = documentLockHandler.getLock(uniqueId);
			try {
				documentLock.writeLock().lock();
				
				if (storeRequest.hasResultDocument()) {
					BSONObject document = BSON.decode(storeRequest.getResultDocument().getDocument().toByteArray());
					
					LumongoSegment s = findSegmentFromUniqueId(uniqueId);
					s.index(uniqueId, document, timestamp);
					
					documentStorage.storeSourceDocument(storeRequest.getUniqueId(), timestamp, document, storeRequest.getResultDocument().getMetadataList());
				}
				
				if (storeRequest.getClearExistingAssociated()) {
					documentStorage.deleteAssociatedDocuments(uniqueId);
				}
				
				for (AssociatedDocument ad : storeRequest.getAssociatedDocumentList()) {
					ad = AssociatedDocument.newBuilder(ad).setTimestamp(timestamp).build();
					documentStorage.storeAssociatedDocument(ad);
				}
			}
			finally {
				documentLock.writeLock().unlock();
			}
			
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public void deleteDocument(DeleteRequest deleteRequest) throws Exception {
		
		indexLock.readLock().lock();
		
		try {
			
			String uniqueId = deleteRequest.getUniqueId();
			
			ReadWriteLock documentLock = documentLockHandler.getLock(uniqueId);
			
			try {
				documentLock.writeLock().lock();
				
				if (deleteRequest.getDeleteDocument()) {
					LumongoSegment s = findSegmentFromUniqueId(deleteRequest.getUniqueId());
					s.deleteDocument(uniqueId);
					documentStorage.deleteSourceDocument(uniqueId);
				}
				
				if (deleteRequest.getDeleteAllAssociated()) {
					documentStorage.deleteAssociatedDocuments(uniqueId);
				}
				else if (deleteRequest.hasFilename()) {
					String fileName = deleteRequest.getFilename();
					documentStorage.deleteAssociatedDocument(uniqueId, fileName);
				}
			}
			finally {
				documentLock.writeLock().unlock();
			}
			
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public Query getQuery(String query, List<String> queryFields, int minimumShouldMatchNumber, Operator defaultOperator) throws Exception {
		indexLock.readLock().lock();
		try {
			
			LumongoQueryParser qp = null;
			if (query == null || query.isEmpty()) {
				return new MatchAllDocsQuery();
			}
			try {
				qp = parsers.borrowObject();
				qp.setMinimumNumberShouldMatch(minimumShouldMatchNumber);
				qp.setDefaultOperator(defaultOperator);
				
				if (queryFields.isEmpty()) {
					qp.setField(indexConfig.getDefaultSearchField());
					return qp.parse(query);
				}
				else {
					BooleanQuery bQuery = new BooleanQuery();
					for (String queryField : queryFields) {
						qp.setField(queryField);
						Query q = qp.parse(query);
						if ((q != null) && // q never null, just being defensive
										(!(q instanceof BooleanQuery) || (((BooleanQuery) q).getClauses().length > 0))) {
							bQuery.add(q, BooleanClause.Occur.SHOULD);
						}
					}
					return bQuery;
				}
				
			}
			finally {
				parsers.returnObject(qp);
			}
			
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public IndexSegmentResponse queryInternal(final QueryWithFilters queryWithFilters, final QueryRequest queryRequest) throws Exception {
		indexLock.readLock().lock();
		try {
			int amount = queryRequest.getAmount();
			if (!queryRequest.getFetchFull() && (amount > 0)) {
				amount = (int) (((queryRequest.getAmount() / numberOfSegments) + indexConfig.getMinSegmentRequest()) * indexConfig.getRequestFactor());
			}
			
			final int requestedAmount = amount;
			
			final HashMap<Integer, FieldDoc> lastScoreDocMap = new HashMap<Integer, FieldDoc>();
			FieldDoc after = null;
			
			LastResult lr = queryRequest.getLastResult();
			if (lr != null) {
				for (LastIndexResult lir : lr.getLastIndexResultList()) {
					if (indexName.equals(lir.getIndexName())) {
						for (ScoredResult sr : lir.getLastForSegmentList()) {
							int docId = sr.getDocId();
							float score = sr.getScore();
							
							SortRequest sortRequest = queryRequest.getSortRequest();
							
							Object[] sortTerms = new Object[sortRequest.getFieldSortCount()];
							
							int sortTermsIndex = 0;
							int stringIndex = 0;
							int intIndex = 0;
							int longIndex = 0;
							int floatIndex = 0;
							int doubleIndex = 0;
							int dateIndex = 0;
							
							for (FieldSort fs : sortRequest.getFieldSortList()) {
								
								String sortField = fs.getSortField();
								
								if (indexConfig.isNumericOrDateField(sortField)) {
									if (indexConfig.isNumericIntField(sortField)) {
										sortTerms[sortTermsIndex] = sr.getSortInteger(intIndex++);
									}
									else if (indexConfig.isNumericLongField(sortField)) {
										sortTerms[sortTermsIndex] = sr.getSortLong(longIndex++);
									}
									else if (indexConfig.isNumericFloatField(sortField)) {
										sortTerms[sortTermsIndex] = sr.getSortFloat(floatIndex++);
									}
									else if (indexConfig.isNumericDoubleField(sortField)) {
										sortTerms[sortTermsIndex] = sr.getSortDouble(doubleIndex++);
									}
									else if (indexConfig.isDateField(sortField)) {
										sortTerms[sortTermsIndex] = sr.getSortDate(dateIndex++);
									}
								}
								else { //string
									sortTerms[sortTermsIndex] = sr.getSortTerm(stringIndex++);
								}
								sortTermsIndex++;
							}
							
							after = new FieldDoc(docId, score, sortTerms, sr.getSegment());
							lastScoreDocMap.put(sr.getSegment(), after);
						}
					}
				}
			}
			
			IndexSegmentResponse.Builder builder = IndexSegmentResponse.newBuilder();
			
			List<Future<SegmentResponse>> responses = new ArrayList<Future<SegmentResponse>>();
			
			for (final LumongoSegment segment : segmentMap.values()) {
				
				Future<SegmentResponse> response = segmentPool.submit(new Callable<SegmentResponse>() {
					
					@Override
					public SegmentResponse call() throws Exception {
						return segment.querySegment(queryWithFilters, requestedAmount, lastScoreDocMap.get(segment.getSegmentNumber()), queryRequest
										.getFacetRequest(), queryRequest.getSortRequest(), queryRequest.getRealTime(), new QueryCacheKey(queryRequest));
					}
					
				});
				
				responses.add(response);
				
			}
			
			for (Future<SegmentResponse> response : responses) {
				try {
					SegmentResponse rs = response.get();
					builder.addSegmentReponse(rs);
				}
				catch (ExecutionException e) {
					Throwable t = e.getCause();
					
					if (t instanceof OutOfMemoryError) {
						throw (OutOfMemoryError) t;
					}
					
					throw ((Exception) e.getCause());
				}
			}
			
			builder.setIndexName(indexName);
			return builder.build();
		}
		finally {
			indexLock.readLock().unlock();
		}
		
	}
	
	public Integer getNumberOfSegments() {
		return numberOfSegments;
	}
	
	public double getSegmentTolerance() {
		return indexConfig.getSegmentTolerance();
	}
	
	private void storeIndexSettings() {
		indexLock.writeLock().lock();
		try {
			DB db = mongo.getDB(mongoConfig.getDatabaseName());
			DBCollection dbCollection = db.getCollection(indexConfig.getIndexName() + CONFIG_SUFFIX);
			DBObject settings = indexConfig.toDBObject();
			settings.put(MongoConstants.StandardFields._ID, SETTINGS_ID);
			dbCollection.save(settings);
		}
		finally {
			indexLock.writeLock().unlock();
		}
		
	}
	
	public void reloadIndexSettings() throws Exception {
		indexLock.writeLock().lock();
		try {
			
			IndexConfig newIndexConfig = loadIndexSettings(mongo, mongoConfig.getDatabaseName(), indexName);
			
			IndexSettings indexSettings = newIndexConfig.getIndexSettings();
			indexConfig.configure(indexSettings);
			
			parsers.clear();
			
			//force analyzer to be fetched first so it doesn't fail only on one segment below
			lumongoAnalyzerFactory.getAnalyzer();
			for (LumongoSegment s : segmentMap.values()) {
				try {
					s.updateIndexSettings(indexSettings, lumongoAnalyzerFactory.getAnalyzer());
				}
				catch (Exception e) {
				}
			}
			
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	public void optimize() throws Exception {
		indexLock.readLock().lock();
		try {
			for (final LumongoSegment segment : segmentMap.values()) {
				segment.optimize();
			}
			
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public GetNumberOfDocsResponse getNumberOfDocs(final boolean realTime) throws Exception {
		indexLock.readLock().lock();
		try {
			List<Future<SegmentCountResponse>> responses = new ArrayList<Future<SegmentCountResponse>>();
			
			for (final LumongoSegment segment : segmentMap.values()) {
				
				Future<SegmentCountResponse> response = segmentPool.submit(new Callable<SegmentCountResponse>() {
					
					@Override
					public SegmentCountResponse call() throws Exception {
						return segment.getNumberOfDocs(realTime);
					}
					
				});
				
				responses.add(response);
				
			}
			
			GetNumberOfDocsResponse.Builder responseBuilder = GetNumberOfDocsResponse.newBuilder();
			
			responseBuilder.setNumberOfDocs(0);
			for (Future<SegmentCountResponse> response : responses) {
				try {
					SegmentCountResponse scr = response.get();
					responseBuilder.addSegmentCountResponse(scr);
					responseBuilder.setNumberOfDocs(responseBuilder.getNumberOfDocs() + scr.getNumberOfDocs());
				}
				catch (InterruptedException e) {
					throw new Exception("Interrupted while waiting for segment results");
				}
				catch (Exception e) {
					Throwable cause = e.getCause();
					if (cause instanceof Exception) {
						throw e;
					}
					
					throw e;
				}
			}
			
			return responseBuilder.build();
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public GetFieldNamesResponse getFieldNames() throws Exception {
		indexLock.readLock().lock();
		try {
			List<Future<GetFieldNamesResponse>> responses = new ArrayList<Future<GetFieldNamesResponse>>();
			
			for (final LumongoSegment segment : segmentMap.values()) {
				
				Future<GetFieldNamesResponse> response = segmentPool.submit(new Callable<GetFieldNamesResponse>() {
					
					@Override
					public GetFieldNamesResponse call() throws Exception {
						return segment.getFieldNames();
					}
					
				});
				
				responses.add(response);
				
			}
			
			GetFieldNamesResponse.Builder responseBuilder = GetFieldNamesResponse.newBuilder();
			
			Set<String> fields = new HashSet<String>();
			for (Future<GetFieldNamesResponse> response : responses) {
				try {
					GetFieldNamesResponse gfnr = response.get();
					fields.addAll(gfnr.getFieldNameList());
				}
				catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof Exception) {
						throw e;
					}
					else {
						throw new Exception(cause);
					}
				}
			}
			
			fields.remove(LumongoConstants.TIMESTAMP_FIELD);
			fields.remove(LumongoConstants.LUCENE_FACET_FIELD);
			responseBuilder.addAllFieldName(fields);
			return responseBuilder.build();
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public void clear() throws Exception {
		indexLock.writeLock().lock();
		try {
			List<Future<Void>> responses = new ArrayList<Future<Void>>();
			
			for (final LumongoSegment segment : segmentMap.values()) {
				
				Future<Void> response = segmentPool.submit(new Callable<Void>() {
					
					@Override
					public Void call() throws Exception {
						segment.clear();
						return null;
					}
					
				});
				
				responses.add(response);
				
			}
			
			for (Future<Void> response : responses) {
				try {
					response.get();
				}
				catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof Exception) {
						throw e;
					}
					else {
						throw new Exception(cause);
					}
				}
			}
			
			documentStorage.deleteAllDocuments();
			
		}
		finally {
			indexLock.writeLock().unlock();
		}
	}
	
	public GetTermsResponse getTerms(final GetTermsRequest request) throws Exception {
		indexLock.readLock().lock();
		try {
			List<Future<GetTermsResponse>> responses = new ArrayList<Future<GetTermsResponse>>();
			
			for (final LumongoSegment segment : segmentMap.values()) {
				
				Future<GetTermsResponse> response = segmentPool.submit(new Callable<GetTermsResponse>() {
					
					@Override
					public GetTermsResponse call() throws Exception {
						return segment.getTerms(request);
					}
					
				});
				
				responses.add(response);
				
			}
			
			GetTermsResponse.Builder responseBuilder = GetTermsResponse.newBuilder();
			
			//not threaded but atomic long is convenient
			TreeMap<String, AtomicLong> terms = new TreeMap<String, AtomicLong>();
			
			for (Future<GetTermsResponse> response : responses) {
				try {
					GetTermsResponse gtr = response.get();
					for (Term term : gtr.getTermList()) {
						if (!terms.containsKey(term.getValue())) {
							terms.put(term.getValue(), new AtomicLong());
						}
						terms.get(term.getValue()).addAndGet(term.getDocFreq());
					}
				}
				catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof Exception) {
						throw e;
					}
					else {
						throw new Exception(cause);
					}
				}
			}
			
			int amountToReturn = Math.min(request.getAmount(), terms.size());
			for (int i = 0; i < amountToReturn; i++) {
				String value = terms.firstKey();
				AtomicLong docFreq = terms.remove(value);
				responseBuilder.addTerm(Term.newBuilder().setValue(value).setDocFreq(docFreq.get()));
			}
			return responseBuilder.build();
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	private static IndexConfig loadIndexSettings(Mongo mongo, String database, String indexName) throws InvalidIndexConfig {
		DB db = mongo.getDB(database);
		DBCollection dbCollection = db.getCollection(indexName + CONFIG_SUFFIX);
		DBObject settings = dbCollection.findOne(new BasicDBObject(MongoConstants.StandardFields._ID, SETTINGS_ID));
		if (settings == null) {
			throw new InvalidIndexConfig(indexName, "Index settings not found");
		}
		IndexConfig indexConfig = IndexConfig.fromDBObject(settings);
		return indexConfig;
	}
	
	public static LumongoIndex loadIndex(HazelcastManager hazelcastManager, MongoConfig mongoConfig, Mongo mongo, ClusterConfig clusterConfig, String indexName)
					throws InvalidIndexConfig, UnknownHostException, MongoException {
		IndexConfig indexConfig = loadIndexSettings(mongo, mongoConfig.getDatabaseName(), indexName);
		log.info("Loading index <" + indexName + ">");
		
		LumongoIndex i = new LumongoIndex(hazelcastManager, mongoConfig, clusterConfig, indexConfig);
		return i;
		
	}
	
	public static LumongoIndex createIndex(HazelcastManager hazelcastManager, MongoConfig mongoConfig, ClusterConfig clusterConfig, IndexConfig indexConfig)
					throws UnknownHostException, MongoException {
		log.info("Creating index <" + indexConfig.getIndexName() + ">: " + indexConfig);
		LumongoIndex i = new LumongoIndex(hazelcastManager, mongoConfig, clusterConfig, indexConfig);
		i.storeIndexSettings();
		return i;
		
	}
	
	//handle forwarding, and locking here like other store requests
	public void storeAssociatedDocument(String uniqueId, String fileName, InputStream is, boolean compress, long clusterTime,
					HashMap<String, String> metadataMap) throws Exception {
		indexLock.readLock().lock();
		try {
			documentStorage.storeAssociatedDocument(uniqueId, fileName, is, compress, clusterTime, metadataMap);
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public InputStream getAssociatedDocumentStream(String uniqueId, String fileName) throws IOException {
		indexLock.readLock().lock();
		try {
			return documentStorage.getAssociatedDocumentStream(uniqueId, fileName);
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public ResultDocument getSourceDocument(String uniqueId, FetchType resultFetchType, List<String> fieldsToReturn, List<String> fieldsToMask)
					throws Exception {
		indexLock.readLock().lock();
		try {
			return documentStorage.getSourceDocument(uniqueId, resultFetchType, fieldsToReturn, fieldsToMask);
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public AssociatedDocument getAssociatedDocument(String uniqueId, String fileName, FetchType associatedFetchType) throws Exception {
		indexLock.readLock().lock();
		try {
			return documentStorage.getAssociatedDocument(uniqueId, fileName, associatedFetchType);
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
	public List<AssociatedDocument> getAssociatedDocuments(String uniqueId, FetchType associatedFetchType) throws Exception {
		indexLock.readLock().lock();
		try {
			return documentStorage.getAssociatedDocuments(uniqueId, associatedFetchType);
		}
		finally {
			indexLock.readLock().unlock();
		}
	}
	
}
