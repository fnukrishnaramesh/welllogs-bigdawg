package istc.bigdawg.executor.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jgrapht.Graphs;

import istc.bigdawg.catalog.CatalogViewer;
import istc.bigdawg.exceptions.BigDawgException;
import istc.bigdawg.islands.QueryContainerForCommonDatabase;
import istc.bigdawg.islands.IslandAndCastResolver;
import istc.bigdawg.islands.IslandAndCastResolver.Scope;
import istc.bigdawg.islands.operators.CommonTableExpressionScan;
import istc.bigdawg.islands.operators.Join;
import istc.bigdawg.islands.operators.Merge;
import istc.bigdawg.islands.operators.Operator;
import istc.bigdawg.query.ConnectionInfo;
import istc.bigdawg.query.ConnectionInfoParser;
import istc.bigdawg.shims.Shim;

public class ExecutionNodeFactory {
	static final Logger log = Logger.getLogger(ExecutionNodeFactory.class.getName());

	private static int maxSerial = 0;

	/**
	 * Produces a String representation of an ExecutionNode
	 *
	 * @param node The ExecutionNode we want to make into a String
	 * @return The representation
	 */
	public static String executionNodeToString(ExecutionNode node) {
		if (node.getClass().getName().contains("BinaryJoinExecutionNode")) {
			return node.serialize();
		}

		StringBuilder currentRep = new StringBuilder();
		currentRep.append("(");

		Optional<String> queryString = node.getQueryString();
		if (queryString.isPresent()) {
			currentRep.append("QUERY:");
			currentRep.append(queryString.get());
		}

		Optional<String> tableName = node.getTableName();
		if (tableName.isPresent()) {
			currentRep.append("TABLE:");
			currentRep.append(tableName.get());
		}

		currentRep.append("ENGINE:(");
		currentRep.append(ConnectionInfoParser.connectionInfoToString(node.getEngine()));
		currentRep.append(")");

		currentRep.append(String.format("NODETYPE:%s", node.getClass().getName()));
		currentRep.append(")");
		return currentRep.toString();
	}

	/**
	 * Produces an ExecutionNode from the output of executionNodeToString
	 *
	 * @param representation an output of executionNodeToString
	 * @return the ExecutionNode
	 */
	public static ExecutionNode stringToExecutionNode(String representation) {
		Pattern queryTable = Pattern.compile("(?<=QUERY:)(?s).*(?=TABLE:)");
		Pattern queryEngine = Pattern.compile("(?<=QUERY:)(?s).*(?=ENGINE:)");
		Pattern table = Pattern.compile("(?<=TABLE:)(?s).*(?=ENGINE:)");
		Pattern engine = Pattern.compile("(?<=ENGINE:\\()[^\\)]*(?=\\))");
		Pattern nodeType = Pattern.compile("(?<=NODETYPE:)[^\\)]*(?=\\))");

		// Get the type of ExecutionNode
		String nodeClass = "LocalQueryExecutionNode";
		Matcher m = nodeType.matcher(representation);
		if (m.find()) {
			nodeClass = m.group();
		}

		if (nodeClass.contains("BinaryJoinExecutionNode")) {
			return BinaryJoinExecutionNode.deserialize(representation);
		}

		// Extract the query
		Optional<String> query = Optional.empty();
		if (representation.contains("QUERY:")) {
			if (representation.contains("TABLE:")) {
				m = queryTable.matcher(representation);
				if (m.find()) {
					query = Optional.of(m.group());
				}
			} else {
				m = queryEngine.matcher(representation);
				if (m.find()) {
					query = Optional.of(m.group());
				}
			}
		}

		// Extract the tableName
		Optional<String> tableName = Optional.empty();
		if (representation.contains("TABLE:")) {
			m = table.matcher(representation);
			if (m.find()) {
				tableName = Optional.of(m.group());
			}
		}

		// Extract the ConnectionInfo
		m = engine.matcher(representation);
		String engineInfo = "";
		if (m.find()) {
			engineInfo = m.group();
		}
		ConnectionInfo connectionInfo = ConnectionInfoParser.stringToConnectionInfo(engineInfo);


		ExecutionNode result = null;
		if (nodeClass.contains("LocalQueryExecutionNode")) {
			result = new LocalQueryExecutionNode(query.get(), connectionInfo, tableName.get());
		} else if (nodeClass.contains("TableExecutionNode")) {
			result = new TableExecutionNode(connectionInfo, tableName.get());
		}
		return result;
	}

	@Deprecated
	public static Map<String, Operator> traverseAndPickOutWiths(Operator root) throws Exception {
		Map<String, Operator> result = new HashMap<>();
		maxSerial++;
		result.put("BIGDAWG_MAIN_" + maxSerial, root);//.generateSelectForExecutionTree(queryPlan.getStatement(), null));

		List<Operator> treeWalker = root.getChildren();
		while (treeWalker.size() > 0) {
			List<Operator> nextGeneration = new ArrayList<Operator>();
			for (Operator c : treeWalker) {

				nextGeneration.addAll(c.getChildren());
				if (c instanceof CommonTableExpressionScan) {
					CommonTableExpressionScan co = ((CommonTableExpressionScan) c);
					String name = co.getSourceTableName();//.getTable().getName();
					result.put(name, co);//co.generateSelectForExecutionTree(queryPlan.getStatement(), name));
					nextGeneration.add(co.getSourceStatement());
				}
			}
			treeWalker = nextGeneration;
		}
		return result;
	}

	/**
	 * Creating a binary join execution node, assume joining along one and only one dimension
	 * 
	 * @param broadcastQuery
	 * @param engine
	 * @param joinDestinationTable
	 * @param joinOp
	 * @param island
	 * @return
	 * @throws Exception
	 */
	private static ExecutionNode createJoinNode(String broadcastQuery, ConnectionInfo engine, int dbid, String joinDestinationTable, Join joinOp, Scope island) throws Exception {

		Shim gen = IslandAndCastResolver.getShim(island, dbid);

		// Break apart Join Predicate Objects into usable Strings
		// It used to be just 3 items list: comparator string, table-column string for left, table-column string for right
		// currently, we employ a 5 item list, breaking the tables and columns apart. 
		List<String> predicateObjects = gen.getJoinPredicate(joinOp);

		if (predicateObjects.isEmpty()) {
			return new LocalQueryExecutionNode(broadcastQuery, engine, joinDestinationTable);
		}

		String comparator = predicateObjects.get(0);
		String leftTable = predicateObjects.get(1);
		String leftAttribute = predicateObjects.get(2);
		String rightTable = predicateObjects.get(3);
		String rightAttribute = predicateObjects.get(4);

		String shuffleLeftJoinQuery = gen.getSelectIntoQuery(joinOp, joinDestinationTable + "_LEFTRESULTS", false).replace(rightTable, joinDestinationTable + "_RIGHTPARTIAL");
		String shuffleRightJoinQuery = gen.getSelectIntoQuery(joinOp, joinDestinationTable + "_RIGHTRESULTS", false).replace(leftTable, joinDestinationTable + "_LEFTPARTIAL");

		BinaryJoinExecutionNode.JoinOperand leftOp = new BinaryJoinExecutionNode.JoinOperand(engine, leftTable, leftAttribute, shuffleLeftJoinQuery);
		BinaryJoinExecutionNode.JoinOperand rightOp = new BinaryJoinExecutionNode.JoinOperand(engine, rightTable, rightAttribute, shuffleRightJoinQuery);

		return new BinaryJoinExecutionNode(broadcastQuery, engine, joinDestinationTable, leftOp, rightOp, comparator);
	}

	
	
	private static int getLeftDeepDBID(Operator o, Map<String, Integer> containers) throws NumberFormatException, Exception {
		Operator leftDeepChild = o;
		while (!leftDeepChild.getChildren().isEmpty() && !leftDeepChild.isPruned()) leftDeepChild = leftDeepChild.getChildren().get(0);
		if (leftDeepChild.getChildren().isEmpty() && !leftDeepChild.isPruned()) throw new BigDawgException("Leave child encountered; should observe pruned child.");
		return containers.get(leftDeepChild.getPruneToken());
	};
	

	
	
	private static ExecutionNodeSubgraph buildOperatorSubgraphNew(Operator op, String dest, Map<String, Integer> containerDBID,
			Map<String, LocalQueryExecutionNode> containerNodes, boolean isSelect, Scope island) throws Exception {
		
		// need to first check if it is a join. 
		int dbid = getLeftDeepDBID(op, containerDBID);
		ConnectionInfo engine = CatalogViewer.getConnectionInfo(dbid);

		Shim gen = IslandAndCastResolver.getShim(island, dbid);

		Operator joinOp = null;
		String sqlStatementForPresentNonJoinSegment = null;
		
		if (gen != null) {
			Pair<Operator, String> ret = gen.getQueryForNonMigratingSegment(op, isSelect);
			joinOp = ret.getLeft();
			sqlStatementForPresentNonJoinSegment = ret.getRight();
		} else {
			sqlStatementForPresentNonJoinSegment = op.getSubTreeToken();
		}

		System.out.printf("\njoinOp: %s; statement: %s\n", joinOp, sqlStatementForPresentNonJoinSegment);
		
		ExecutionNodeSubgraph result = new ExecutionNodeSubgraph();

		LocalQueryExecutionNode lqn = null;
		if (sqlStatementForPresentNonJoinSegment != null) {
			// this and joinOp == null will not happen at the same time
			lqn = new LocalQueryExecutionNode(sqlStatementForPresentNonJoinSegment, engine, dest);
			result.addVertex(lqn);
			result.exitPoint = lqn;
		}

		if (joinOp != null) {
			String joinDestinationTable = joinOp.getSubTreeToken();
			
			String broadcastQuery;
			if (sqlStatementForPresentNonJoinSegment == null && isSelect) {
				broadcastQuery = gen.getSelectQuery(joinOp);
			} else {
				broadcastQuery = gen.getSelectIntoQuery(joinOp, joinDestinationTable, true);
			}

			ExecutionNode joinNode = null;
			
			// we want to know the destination DBID
			if (joinOp instanceof Join) joinNode = ExecutionNodeFactory.createJoinNode(broadcastQuery, engine, dbid, joinDestinationTable, (Join)joinOp, island);
			else if (joinOp instanceof Merge) joinNode = new LocalQueryExecutionNode(broadcastQuery, engine, joinDestinationTable);

			result.addVertex(joinNode);

			if (sqlStatementForPresentNonJoinSegment == null) {
				result.exitPoint = joinNode;
			} else {
				result.addEdge(joinNode, result.exitPoint);
			}

			for (Operator child : joinOp.getChildren()) {
				if (child.isPruned()) {
					ExecutionNode containerNode = containerNodes.get(child.getPruneToken());
					result.addVertex(containerNode);
					result.addEdge(containerNode, joinNode);
				} else {
					String token = child.isSubTree() ? child.getSubTreeToken() : null;
					ExecutionNodeSubgraph subgraph = buildOperatorSubgraphNew(child, token, containerDBID, containerNodes, false, island);
					Graphs.addGraph(result, subgraph);
					result.addEdge(subgraph.exitPoint, joinNode);
				}
			}
		}

		return result;
	};
	
	
	
	
	public static void addNodesAndEdgesNew(QueryExecutionPlan qep, Operator remainder, List<String> remainderLoc, Map<String,
			QueryContainerForCommonDatabase> containers, boolean isSelect, String destinationName) throws Exception {
		log.debug(String.format("Creating QEP %s...", qep.getSerializedName()));

		
		
		Map<String, LocalQueryExecutionNode> containerNodes = new HashMap<>();
		Map<String, Integer> containerDBID = new HashMap<>();
		for (Map.Entry<String, QueryContainerForCommonDatabase> entry : containers.entrySet()) {
			String table = entry.getKey();
			QueryContainerForCommonDatabase container = entry.getValue();
			String selectIntoString = container.generateSelectIntoString(qep.getIsland());
			LocalQueryExecutionNode localQueryNode = new LocalQueryExecutionNode(selectIntoString, container.getConnectionInfo(), table);

			containerNodes.put(table, localQueryNode);
			containerDBID.put(table, Integer.parseInt(container.getDBID()));
			
//			System.out.printf("<><><> Container query string: %s; QEP: %s;\n" , selectIntoString, qep.getSerializedName());
		}
//		System.out.println();

		int remainderDBID;
		if (remainderLoc != null) {
			remainderDBID = Integer.parseInt(remainderLoc.get(0));
		} else {
			remainderDBID = getLeftDeepDBID(remainder, containerDBID);
		}

		String remainderSelectIntoString;
		ConnectionInfo remainderCI = CatalogViewer.getConnectionInfo(remainderDBID);
		
		Shim gen = IslandAndCastResolver.getShim(qep.getIsland(), remainderDBID);
		
		remainder.setSubTree(true);
		qep.setTerminalTableName(destinationName);

		
		if (isSelect) remainderSelectIntoString = gen.getSelectQuery(remainder);
		else remainderSelectIntoString = gen.getSelectIntoQuery(remainder, destinationName, false); // FIXME verify this behaves correctly for Accumulo
		
//		log.info(String.format("\n\n<><><> Remainder class: %s; QEP: %s; children count: %s; query string: %s\n"
//				, remainder.getClass().getSimpleName()
//				, qep.getSerializedName()
//				, remainder.getChildren().size()
//				, remainderSelectIntoString));
		
		if (remainderLoc != null) {
			LocalQueryExecutionNode lqn = new LocalQueryExecutionNode(remainderSelectIntoString, remainderCI, destinationName);
			qep.addNode(lqn);
			qep.setTerminalTableNode(lqn);
//			String lStr = String.format("\n\n<><><><><> Loc non null QEP terminal: %s; isSelect?: %s; remainder into: %s <><><><><><><> \n\n\n", qep.getTerminalTableNode().getQueryString(), isSelect, destinationName);
//			log.info(lStr);
		} else {
			ExecutionNodeSubgraph subgraph = buildOperatorSubgraphNew(remainder, destinationName, containerDBID, containerNodes, isSelect, qep.getIsland());
			Graphs.addGraph(qep, subgraph);
			qep.setTerminalTableNode(subgraph.exitPoint);
			String lStr = String.format("\n<><><> Loc null QEP terminal: %s; isSelect?: %s; remainder into: %s\n", qep.getTerminalTableNode().getQueryString(), isSelect, destinationName);
			log.info(lStr);
		}

		log.debug(String.format("Finished creating QEP %s.", qep.getSerializedName()));
	}
}