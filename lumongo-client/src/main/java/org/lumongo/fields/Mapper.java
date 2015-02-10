package org.lumongo.fields;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.lumongo.client.command.BatchFetch;
import org.lumongo.client.command.CreateOrUpdateIndex;
import org.lumongo.client.command.Store;
import org.lumongo.client.config.IndexConfig;
import org.lumongo.client.pool.LumongoWorkPool;
import org.lumongo.client.result.BatchFetchResult;
import org.lumongo.client.result.FetchResult;
import org.lumongo.client.result.QueryResult;
import org.lumongo.cluster.message.Lumongo;
import org.lumongo.cluster.message.Lumongo.FacetAs;
import org.lumongo.cluster.message.Lumongo.FacetAs.LMFacetType;
import org.lumongo.cluster.message.Lumongo.FieldConfig;
import org.lumongo.cluster.message.Lumongo.IndexAs;
import org.lumongo.cluster.message.Lumongo.LMAnalyzer;
import org.lumongo.doc.ResultDocBuilder;
import org.lumongo.fields.annotations.*;
import org.lumongo.util.AnnotationUtil;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class Mapper<T> {
	
	private final Class<T> clazz;
	
	private HashSet<SavedFieldInfo<T>> savedFields;
	
	private UniqueIdFieldInfo<T> uniqueIdField;
	
	private DefaultSearchFieldInfo<T> defaultSearchField;
	
	private Settings settings;
	
	private HashMap<String, FieldConfig> fieldConfigMap;
	
	public Mapper(Class<T> clazz) {
		
		this.savedFields = new HashSet<SavedFieldInfo<T>>();
		
		this.fieldConfigMap = new HashMap<String, FieldConfig>();
		
		this.clazz = clazz;
		
		List<Field> allFields = AnnotationUtil.getNonStaticFields(clazz, true);
		
		for (Field f : allFields) {
			f.setAccessible(true);
			
			String fieldName = f.getName();
			
			if (f.isAnnotationPresent(UniqueId.class)) {
				
				if (f.isAnnotationPresent(AsField.class)) {
					throw new RuntimeException("Cannot use AsField with UniqueId on field <" + f.getName() + "> for class <" + clazz.getSimpleName()
									+ ">.  Unique id always stored as _id.");
				}
				
				@SuppressWarnings("unused")
				UniqueId uniqueId = f.getAnnotation(UniqueId.class);
				
				fieldName = "_id";
				
				if (uniqueIdField == null) {
					uniqueIdField = new UniqueIdFieldInfo<>(f, fieldName);
					
					if (!String.class.equals(f.getType())) {
						throw new RuntimeException("Unique id field must be a String in class <" + clazz.getSimpleName() + ">");
					}
					
				}
				else {
					throw new RuntimeException("Cannot define two unique id fields for class <" + clazz.getSimpleName() + ">");
				}
				
			}
			else if (f.isAnnotationPresent(AsField.class)) {
				AsField as = f.getAnnotation(AsField.class);
				fieldName = as.value();
			}
			
			FieldConfig.Builder fieldConfigBuilder = FieldConfig.newBuilder();
			fieldConfigBuilder.setStoredFieldName(fieldName);
			
			if (f.isAnnotationPresent(NotSaved.class)) {
				@SuppressWarnings("unused")
				NotSaved saved = f.getAnnotation(NotSaved.class);
				
				if (f.isAnnotationPresent(IndexedFields.class) || f.isAnnotationPresent(Indexed.class) || f.isAnnotationPresent(Faceted.class) || f.isAnnotationPresent(UniqueId.class)
								|| f.isAnnotationPresent(DefaultSearch.class)) {
					throw new RuntimeException("Cannot use NotSaved with Indexed, Faceted, UniqueId, DefaultSearch on field <" + f.getName() + "> for class <"
									+ clazz.getSimpleName() + ">");
				}
				
			}
			else {
				savedFields.add(new SavedFieldInfo<T>(f, fieldName));
			}

			if (f.isAnnotationPresent(IndexedFields.class)) {
				IndexedFields in = f.getAnnotation(IndexedFields.class);
				for (Indexed indexed : in.value()) {
					addIndexedField(indexed, fieldName, fieldConfigBuilder);
				}
			}
			else if (f.isAnnotationPresent(Indexed.class)) {
				Indexed in = f.getAnnotation(Indexed.class);
				addIndexedField(in, fieldName, fieldConfigBuilder);
				
			}
			
			if (f.isAnnotationPresent(Faceted.class)) {
				Faceted faceted = f.getAnnotation(Faceted.class);
				
				String facetName = fieldName;
				if (!faceted.name().isEmpty()) {
					facetName = faceted.name();
				}
				
				LMFacetType facetType = faceted.type();
				
				fieldConfigBuilder.addFacetAs(FacetAs.newBuilder().setFacetName(facetName).setFacetType(facetType));
			}
			
			if (f.isAnnotationPresent(DefaultSearch.class)) {
				
				if (!f.isAnnotationPresent(Indexed.class) || !f.isAnnotationPresent(IndexedFields.class)) {
					throw new RuntimeException("DefaultSearch must be on Indexed field <" + f.getName() + "> for class <" + clazz.getSimpleName() + ">");
				}
				
				@SuppressWarnings("unused")
				DefaultSearch defaultSearch = f.getAnnotation(DefaultSearch.class);
				
				if (defaultSearchField == null) {
					defaultSearchField = new DefaultSearchFieldInfo<>(f, fieldName);
				}
				else {
					throw new RuntimeException("Cannot define two default search fields for class <" + clazz.getSimpleName() + ">");
				}
				
			}
			
			if (fieldConfigMap.containsKey(fieldName)) {
				throw new RuntimeException("Duplicate field name <" + fieldName + ">");
			}
			fieldConfigMap.put(fieldName, fieldConfigBuilder.build());
			
		}
		if (uniqueIdField == null) {
			throw new RuntimeException("A unique id field must be defined for class <" + clazz.getSimpleName() + ">");
		}
		
		if (defaultSearchField == null) {
			throw new RuntimeException("A default search field must be defined for class <" + clazz.getSimpleName() + ">");
		}
		
		if (clazz.isAnnotationPresent(Settings.class)) {
			settings = clazz.getAnnotation(Settings.class);
		}
		
	}

	private void addIndexedField(Indexed in, String fieldName, FieldConfig.Builder fieldConfigBuilder) {
		LMAnalyzer analyzer = in.analyzer();

		String indexedFieldName = fieldName;
		if (!in.fieldName().isEmpty()) {
			indexedFieldName = in.fieldName();
		}

		fieldConfigBuilder.addIndexAs(IndexAs.newBuilder().setIndexFieldName(indexedFieldName).setAnalyzer(analyzer));
	}

	public CreateOrUpdateIndex createOrUpdateIndex() {
		
		if (settings == null) {
			throw new RuntimeException("No Settings annotation for class <" + clazz.getSimpleName() + ">");
		}
		
		IndexConfig indexConfig = new IndexConfig(defaultSearchField.getFieldName());
		
		indexConfig.setApplyUncommitedDeletes(settings.applyUncommitedDeletes());
		indexConfig.setRequestFactor(settings.requestFactor());
		indexConfig.setMinSegmentRequest(settings.minSeqmentRequest());
		indexConfig.setIdleTimeWithoutCommit(settings.idleTimeWithoutCommit());
		indexConfig.setSegmentCommitInterval(settings.segmentCommitInterval());
		indexConfig.setBlockCompression(settings.blockCompression());
		indexConfig.setSegmentTolerance(settings.segmentTolerance());
		indexConfig.setSegmentFlushInterval(settings.segmentFlushInterval());
		indexConfig.setSegmentQueryCacheSize(settings.segmentQueryCacheSize());
		indexConfig.setSegmentQueryCacheMaxAmount(settings.segmentQueryCacheMaxAmount());
		
		for (String fieldName : fieldConfigMap.keySet()) {
			indexConfig.addFieldConfig(fieldConfigMap.get(fieldName));
		}
		
		CreateOrUpdateIndex createOrUpdateIndex = new CreateOrUpdateIndex(settings.indexName(), settings.numberOfSegments(), uniqueIdField.getFieldName(),
						indexConfig);
		
		return createOrUpdateIndex;
	}
	
	public Class<T> getClazz() {
		return clazz;
	}
	
	public Store createStore(T object) throws Exception {
		if (settings == null) {
			throw new RuntimeException("No Settings annonation for class <" + clazz.getSimpleName() + ">");
		}
		return createStore(settings.indexName(), object);
	}
	
	public Store createStore(String indexName, T object) throws Exception {
		ResultDocBuilder rd = toResultDocumentBuilder(object);
		Store store = new Store(rd.getUniqueId(), indexName);
		store.setResultDocument(rd);
		return store;
	}
	
	public List<T> fromQueryResult(LumongoWorkPool lumongoWorkPool, QueryResult queryResult) throws Exception {
		BatchFetch batchFetch = new BatchFetch().addFetchDocumentsFromResults(queryResult);
		BatchFetchResult bfr = lumongoWorkPool.batchFetch(batchFetch);
		return fromBatchFetchResult(bfr);
	}
	
	public List<T> fromBatchFetchResult(BatchFetchResult batchFetchResult) throws Exception {
		List<T> results = new ArrayList<T>();
		for (FetchResult fr : batchFetchResult.getFetchResults()) {
			results.add(fr.getDocument(this));
		}
		return results;
	}
	
	public T fromFetchResult(FetchResult fetchResult) throws Exception {
		return fetchResult.getDocument(this);
	}
	
	public ResultDocBuilder toResultDocumentBuilder(T object) throws Exception {
		String uniqueId = uniqueIdField.build(object);
		DBObject document = new BasicDBObject();
		for (SavedFieldInfo<T> sfi : savedFields) {
			Object o = sfi.getValue(object);
			document.put(sfi.getFieldName(), o);
		}
		ResultDocBuilder resultDocumentBuilder = new ResultDocBuilder();
		resultDocumentBuilder.setDocument(document).setUniqueId(uniqueId);
		return resultDocumentBuilder;
	}
	
	public T fromDBObject(DBObject savedDBObject) throws Exception {
		T newInstance = clazz.newInstance();
		for (SavedFieldInfo<T> sfi : savedFields) {
			sfi.populate(newInstance, savedDBObject);
		}
		
		uniqueIdField.populate(newInstance, savedDBObject);
		
		return newInstance;
	}
	
}
