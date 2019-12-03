package istc.bigdawg.islands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import istc.bigdawg.cast.CastOverseer;
import istc.bigdawg.exceptions.BigDawgException;
import istc.bigdawg.exceptions.CastException;
import istc.bigdawg.exceptions.QueryParsingException;
import istc.bigdawg.islands.IslandAndCastResolver.Scope;
import istc.bigdawg.islands.operators.Merge;

public class CrossIslandQueryPlan extends DirectedAcyclicGraph<CrossIslandQueryNode, DefaultEdge> 
	implements Iterable<CrossIslandQueryNode> {

	private static final long serialVersionUID = -3609729432970736589L;
	private Stack<Map<String, String>> transitionSchemas;
	private Set<CrossIslandQueryNode> entryNode;
	private CrossIslandQueryNode terminalNode;
	private static final String outputToken  = "BIGDAWG_OUTPUT";
	private static final String extractTitle = "BIGDAWGTAG_";
	private static final String castTitle = "BIGDAWGCAST_";
	private static int maxSerial = 0;
	private int serial;
	
	public CrossIslandQueryPlan() {
		super(DefaultEdge.class);
		maxSerial++;
		this.serial = maxSerial;
	}
	
	// NEW METHOD
	public CrossIslandQueryPlan(String userinput, Set<Integer> objectsToDelete) throws Exception {
		this();
//		terminalOperatorsForSchemas = new HashMap<>();
		transitionSchemas = new Stack<>();
		entryNode = new HashSet<>();
		addNodes(userinput, objectsToDelete);
		checkAndProcessUnionTerminalOperators();
	}
	
	public void addNodes(String userinput, Set<Integer> objectsToDelete) throws Exception {

		Pattern mark								= IslandAndCastResolver.QueryParsingPattern;
		Matcher matcher								= mark.matcher(userinput);
		
	    Stack<String> stkwrite						= new Stack<>();
	    Stack<Integer> stkparen						= new Stack<>();
	    Stack<Scope> lastScopes						= new Stack<>();
	    
	    // nodes in the last level
	    Stack<List<CrossIslandQueryNode>> nodeStack	= new Stack<>();
	    int lastLevel								= 0; 
	    Scope thisScope								= null;
	    Scope innerScope							= null;
	    
	    int extractionCounter 						= 0;
	    int parenLevel								= 0;
	    int lastStop								= 0;	// location of the place where it stopped last iteration
	    
	    stkwrite.push("");
	    stkparen.push(parenLevel);						// records level of parenthesis
	    
	    while (matcher.find()) {
	    	if ((userinput.charAt(matcher.start()) | 0x20) == 'b') {
	    		// update the prior and add a new one
	    		stkwrite.push(stkwrite.pop() + userinput.substring(lastStop, matcher.start()));
	    		lastStop = matcher.end(); 
	    		stkwrite.push(userinput.substring(matcher.start(), lastStop));
	    		
	    		lastScopes.push(IslandAndCastResolver.convertFunctionScope(userinput.substring(matcher.start(), matcher.end())));
	    		innerScope = null;
//	    		System.out.printf("Last scope: %s\n", lastScopes.peek());
	    		
	    		// add parse level
	    		parenLevel += 1;
	    		stkparen.push(parenLevel);
	    		transitionSchemas.push(new HashMap<>());
	    		nodeStack.push(new ArrayList<>());
	    	} else if (userinput.charAt(matcher.start()) == '(') {
	    		parenLevel += 1;
	    	} else {
	    		if (parenLevel != stkparen.peek()) {
	    			parenLevel -= 1;
	    		} else {
		    		// Pop current scope, because it's no longer useful.
	    			thisScope = lastScopes.pop();
	    			
	    			// finish and extract this entry, add new variable for the prior
	    			String name = null;
	    			
		    		if (parenLevel == 1)
		    			name = CrossIslandQueryPlan.getOutputToken();
		    		else if (!thisScope.equals(Scope.CAST)) {
		    			extractionCounter += 1;
		    			name = extractTitle + extractionCounter;
		    		} else 
		    			name = castTitle + parenLevel;
		    			
		    		
		    		// NEW
		    		Scope outterScope = lastScopes.isEmpty() ? null : lastScopes.peek();
//		    		System.out.printf("This Scope: %s; Outter Scope: %s\n", thisScope, outterScope);
		    		CrossIslandQueryNode newNode = createVertex(name, stkwrite.pop() + userinput.substring(lastStop, matcher.end()), thisScope, innerScope, outterScope, objectsToDelete);
		    		
		    		innerScope = thisScope;
		    		
		    		// if lastLevel is this level + 1, then connect everything and clear
		    		// otherwise if lastLevel is the same, then add this one
		    		// otherwise complain
		    		this.addVertex(newNode);
		    		if (nodeStack.peek().isEmpty()) 
		    			entryNode.add(newNode);
		    		if (name.equals(getOutputToken()))
		    			terminalNode = newNode;
		    		
		    		List<CrossIslandQueryNode> temp = nodeStack.pop();
		    		if (lastLevel <= parenLevel) {
		    			if (!nodeStack.isEmpty()) 
		    				nodeStack.peek().add(newNode);
		    		} else if (lastLevel > parenLevel ) {//+ 1) {
		    			for (CrossIslandQueryNode p : temp) {
	    					// create new edge
							this.addDagEdge(p, newNode);
		    			}
		    			if (!nodeStack.isEmpty()) 
		    				nodeStack.peek().add(newNode);
		    		} 
		    		
		    		lastLevel = parenLevel;
		    		// NEW END
		    		stkwrite.push(stkwrite.pop() + newNode.getName()); //extractTitle + extractionCounter);
		    		
//		    		if (! (newNode instanceof CrossIslandCastNode))
//	    			extractionCounter += 1;
		    		lastStop = matcher.end(); 
		    		
		    		// subtract one par level
		    		parenLevel -= 1;
		    		stkparen.pop();
		    		
	    		}
	    	}
	    }
		
	}
	
	private void checkAndProcessUnionTerminalOperators() {
		// TODO check the last operator and process it
		if (terminalNode instanceof IntraIslandQuery 
				&& ((IntraIslandQuery)terminalNode).getRemainderLoc() == null
				&& ((IntraIslandQuery)terminalNode).getRemainder(0) instanceof Merge) {
//			IntraIslandQuery node = (IntraIslandQuery) terminalNode;
			
		}
	}
	
	private CrossIslandQueryNode createVertex(String name, String rawQueryString, 
			Scope thisScope, Scope innerScope, Scope outerScope, Set<Integer> catalogSOD) throws BigDawgException {
		
		// IDENTIFY ISLAND AND STRIP
		Matcher islandMatcher	= IslandAndCastResolver.ScopeStartPattern.matcher(rawQueryString);
		Matcher queryEndMatcher = IslandAndCastResolver.ScopeEndPattern.matcher(rawQueryString);
		
		CrossIslandQueryNode newNode;
		
		if (islandMatcher.find() && queryEndMatcher.find()) {
			
			String islandQuery = rawQueryString.substring(islandMatcher.end(), queryEndMatcher.start());

			// check scope and direct traffic
			if (thisScope.equals(Scope.CAST)) {

				CrossIslandCast castNode = null;
				
				Matcher castSchemaMatcher = IslandAndCastResolver.CastSchemaPattern.matcher(islandQuery);
				if (castSchemaMatcher.find()) {
					
					Matcher castNameMatcher = IslandAndCastResolver.CastNamePattern.matcher(islandQuery);
					if (!castNameMatcher.find()) throw new CastException("Cannot find name for Cast result: "+ islandQuery);
					
					// check if CAST exists
					if (!CastOverseer.isCastAllowed(innerScope, outerScope)) {
						throw new CastException(String.format("Cast from %s island to %s island is not allowed", innerScope.name(), outerScope.name()));
					};
					castNode = new CrossIslandCast(innerScope, outerScope, 
							islandQuery.substring(castSchemaMatcher.start(), castSchemaMatcher.end()), 
							islandQuery.substring(castNameMatcher.start(), castNameMatcher.end()));
				} else 
					throw new CastException("Invalid Schema for Cast: "+ islandQuery);
				
				Matcher castSourceScopeMatcher = IslandAndCastResolver.CastScopePattern.matcher(islandQuery);
				// if not found then we rely on edge to make it happen
				if (castSourceScopeMatcher.find()) {
					castNode.setDestinationScope(IslandAndCastResolver.convertDestinationScope(islandQuery.substring(castSourceScopeMatcher.start(), castSourceScopeMatcher.end())));
				} 
				
				Island destIsland = IslandAndCastResolver.getIsland(castNode.getDestinationScope());
				
				transitionSchemas.pop();
				transitionSchemas.peek().put(castNode.getName(), 
						destIsland.getCreateStatementForTransitionTable(castNode.getName(), islandQuery.substring(castSchemaMatcher.start(), castSchemaMatcher.end())));
				
				// add catalog entires
				catalogSOD.add(destIsland.addCatalogObjectEntryForTemporaryTable(castNode.getName()));
				
				newNode = castNode;
				
			} else if (IslandAndCastResolver.isOperatorBasedIsland(thisScope)) {
				newNode = IslandAndCastResolver.getIsland(thisScope)
						.getIntraIslandQuery(islandQuery, name, transitionSchemas.pop());
			} else {
				newNode = new CrossIslandNonOperatorNode(thisScope, islandQuery, name);
			}
			
		} else 
			throw new QueryParsingException("Matcher cannot find token; query string: "+ rawQueryString);
		
		return newNode;
	}; 
	
	// NEW METHOD END
	
	
	public CrossIslandQueryNode getTerminalNode() {
		return terminalNode;
	}
	
	public Stack<Map<String, String>> getTransitionSchemas() {
		return transitionSchemas;
	}
	
	public int getSerial() {
		return serial;
	}
	
	public Set<CrossIslandQueryNode> getEntryNodes() {
		return entryNode;
	} 
	
	public static String getOutputToken () {
		return new String (outputToken);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Set<CrossIslandQueryNode> nodeList = new HashSet<>();
		nodeList.addAll(this.getEntryNodes());
		while (!nodeList.isEmpty()) {
			Set<CrossIslandQueryNode> nextGen = new HashSet<>();
			for (CrossIslandQueryNode n : nodeList) {
				for (DefaultEdge e : this.edgesOf(n)) {
					if (this.getEdgeTarget(e) == n)  continue;
					sb.append('(').append(this.getEdgeSource(e)).append(" -> ").append(this.getEdgeTarget(e)).append(")\n");
					nextGen.add(this.getEdgeTarget(e));
				}
			}
			nodeList = nextGen; 
		}
		return sb.toString();
	}
}
