package org.lumongo.admin;

import java.text.DecimalFormat;
import java.util.List;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.lumongo.LumongoConstants;
import org.lumongo.admin.help.LumongoHelpFormatter;
import org.lumongo.client.command.BatchFetch;
import org.lumongo.client.command.Query;
import org.lumongo.client.config.LumongoPoolConfig;
import org.lumongo.client.pool.LumongoBaseWorkPool;
import org.lumongo.client.pool.LumongoPool;
import org.lumongo.client.result.BatchFetchResult;
import org.lumongo.client.result.FetchResult;
import org.lumongo.client.result.QueryResult;
import org.lumongo.cluster.message.Lumongo.FacetCount;
import org.lumongo.cluster.message.Lumongo.FacetGroup;
import org.lumongo.cluster.message.Lumongo.FieldSort.Direction;
import org.lumongo.cluster.message.Lumongo.ScoredResult;
import org.lumongo.util.LogUtil;

public class Search {
	
	public static void main(String[] args) throws Exception {
		
		DecimalFormat df = new DecimalFormat("#.00");
		
		LogUtil.loadLogConfig();
		
		OptionParser parser = new OptionParser();
		OptionSpec<String> addressArg = parser.accepts(AdminConstants.ADDRESS, "LuMongo server address").withRequiredArg().defaultsTo("localhost");
		OptionSpec<Integer> portArg = parser.accepts(AdminConstants.PORT, "LuMongo external port").withRequiredArg().ofType(Integer.class)
						.defaultsTo(LumongoConstants.DEFAULT_EXTERNAL_SERVICE_PORT);
		OptionSpec<String> indexesArg = parser.accepts(AdminConstants.INDEX, "Index to search").withRequiredArg().required();
		OptionSpec<String> queryArg = parser.accepts(AdminConstants.QUERY, "Lucene query (matches all docs by default)").withRequiredArg();
		OptionSpec<Integer> amountArg = parser.accepts(AdminConstants.AMOUNT, "Amount of results to return").withRequiredArg().ofType(Integer.class)
						.defaultsTo(10);
		OptionSpec<Boolean> realTimeArg = parser.accepts(AdminConstants.REAL_TIME, "Real time search").withRequiredArg().ofType(Boolean.class);
		OptionSpec<String> facetsArg = parser.accepts(AdminConstants.FACET, "Count facets on").withRequiredArg();
		OptionSpec<String> drillDownArg = parser.accepts(AdminConstants.DRILL_DOWN, "Drill down on").withRequiredArg();
		OptionSpec<String> sortArg = parser.accepts(AdminConstants.SORT, "Field to sort on").withRequiredArg();
		OptionSpec<String> sortDescArg = parser.accepts(AdminConstants.SORT_DESC, "Field to sort on (descending)").withRequiredArg();
		OptionSpec<String> queryFieldArg = parser.accepts(AdminConstants.QUERY_FIELD,
						"Specific field(s) for query to search if none specified in query instead of index default").withRequiredArg();
		OptionSpec<String> filterQueryArg = parser.accepts(AdminConstants.FILTER_QUERY, "Filter query").withRequiredArg();
		
		OptionSpec<Void> fetchArg = parser.accepts(AdminConstants.FETCH);
		
		try {
			OptionSet options = parser.parse(args);
			
			List<String> indexes = options.valuesOf(indexesArg);
			String address = options.valueOf(addressArg);
			int port = options.valueOf(portArg);
			String query = options.valueOf(queryArg);
			int amount = options.valueOf(amountArg);
			Boolean realTime = options.valueOf(realTimeArg);
			List<String> facets = options.valuesOf(facetsArg);
			List<String> drillDowns = options.valuesOf(drillDownArg);
			List<String> sortList = options.valuesOf(sortArg);
			List<String> sortDescList = options.valuesOf(sortDescArg);
			List<String> queryFieldsList = options.valuesOf(queryFieldArg);
			List<String> filterQueryList = options.valuesOf(filterQueryArg);
			
			boolean fetch = options.has(fetchArg);
			
			LumongoPoolConfig lumongoPoolConfig = new LumongoPoolConfig();
			lumongoPoolConfig.addMember(address, port);
			LumongoBaseWorkPool lumongoWorkPool = new LumongoBaseWorkPool(new LumongoPool(lumongoPoolConfig));
			
			try {
				
				Query q = new Query(indexes, query, amount);
				
				for (String facet : facets) {
					q.addCountRequest(facet);
				}
				
				for (String drillDown : drillDowns) {
					q.addDrillDown(drillDown);
				}
				
				for (String sort : sortList) {
					
					q.addFieldSort(sort);
				}
				
				for (String sortDesc : sortDescList) {
					q.addFieldSort(sortDesc, Direction.DESCENDING);
				}
				
				for (String queryField : queryFieldsList) {
					q.addQueryField(queryField);
				}
				
				for (String filterQuery : filterQueryList) {
					q.addFilterQuery(filterQuery);
				}
				
				q.setRealTime(realTime);
				
				QueryResult qr = lumongoWorkPool.execute(q);
				
				List<ScoredResult> srList = qr.getResults();
				
				System.out.println("QueryTime: " + (qr.getCommandTimeMs()) + "ms");
				System.out.println("TotalResults: " + qr.getTotalHits());
				
				System.out.println("Results:");
				
				System.out.print("UniqueId");
				System.out.print("\t");
				System.out.print("Score");
				System.out.print("\t");
				System.out.print("Index");
				System.out.print("\t");
				System.out.print("Segment");
				System.out.print("\t");
				System.out.print("SegmentId");
				System.out.print("\t");
				System.out.print("Sort");
				System.out.println();
				
				for (ScoredResult sr : srList) {
					System.out.print(sr.getUniqueId());
					System.out.print("\t");
					System.out.print(df.format(sr.getScore()));
					System.out.print("\t");
					System.out.print(sr.getIndexName());
					System.out.print("\t");
					System.out.print(sr.getSegment());
					System.out.print("\t");
					System.out.print(sr.getDocId());
					System.out.print("\t");
					
					StringBuffer sb = new StringBuffer();
					
					for (String s : sr.getSortTermList()) {
						if (sb.length() != 0) {
							sb.append(",");
						}
						sb.append(s);
					}
					for (Integer i : sr.getSortIntegerList()) {
						if (sb.length() != 0) {
							sb.append(",");
						}
						sb.append(i);
					}
					for (Long l : sr.getSortLongList()) {
						if (sb.length() != 0) {
							sb.append(",");
						}
						sb.append(l);
					}
					for (Float f : sr.getSortFloatList()) {
						if (sb.length() != 0) {
							sb.append(",");
						}
						sb.append(f);
					}
					for (Double d : sr.getSortDoubleList()) {
						if (sb.length() != 0) {
							sb.append(",");
						}
						sb.append(d);
					}
					
					if (sb.length() != 0) {
						System.out.print(sb);
					}
					else {
						System.out.print("--");
					}
					
					System.out.println();
				}
				
				if (!qr.getFacetGroups().isEmpty()) {
					System.out.println("Facets:");
					for (FacetGroup fg : qr.getFacetGroups()) {
						System.out.println();
						System.out.println("--Facet on " + fg.getCountRequest().getFacetField().getLabel() + "--");
						for (FacetCount fc : fg.getFacetCountList()) {
							System.out.print(fc.getFacet());
							System.out.print("\t");
							System.out.print(fc.getCount());
							System.out.println();
						}
					}
					
				}
				
				if (fetch) {
					System.out.println("\nDocuments\n");
					BatchFetch batchFetch = new BatchFetch();
					batchFetch.addFetchDocumentsFromResults(srList);
					
					BatchFetchResult bfr = lumongoWorkPool.execute(batchFetch);
					
					for (FetchResult fetchResult : bfr.getFetchResults()) {
						System.out.println();
						
						if (fetchResult.hasResultDocument()) {
							System.out.println(fetchResult.getUniqueId() + ":\n" + fetchResult.getDocument());
						}
						else {
							System.out.println(fetchResult.getUniqueId() + ":\n" + "Failed to fetch");
						}
					}
				}
			}
			finally {
				if (lumongoWorkPool != null) {
					lumongoWorkPool.shutdown();
				}
			}
		}
		catch (OptionException e) {
			System.err.println("ERROR: " + e.getMessage());
			parser.formatHelpWith(new LumongoHelpFormatter());
			parser.printHelpOn(System.out);
		}
	}
}
