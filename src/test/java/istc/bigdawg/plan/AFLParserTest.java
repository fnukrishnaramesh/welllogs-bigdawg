package istc.bigdawg.plan;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.Test;

import costmodel.StringUnitCostModel;
import distance.APTED;
import istc.bigdawg.catalog.CatalogViewer;
import istc.bigdawg.islands.SciDB.AFLPlanParser;
import istc.bigdawg.islands.SciDB.AFLQueryPlan;
import istc.bigdawg.islands.operators.Operator;
import istc.bigdawg.planner.Planner;
import istc.bigdawg.scidb.SciDBConnectionInfo;
import istc.bigdawg.scidb.SciDBHandler;
import istc.bigdawg.shims.AFLQueryGenerator;
import istc.bigdawg.shims.OperatorQueryGenerator;
import junit.framework.TestCase;
import node.Node;
import node.StringNodeData;
import parser.BracketStringInputParser;


public class AFLParserTest extends TestCase {
	private static Map<String, String> expectedOutputs;
	private static Map<String, String> input1;
	private static Map<String, String> input2;
	
	protected void setUp() throws Exception {
		expectedOutputs = new HashMap<>();
		
		input1 = new HashMap<>();
		input2 = new HashMap<>();
		
		setupParse();
//		setupTreeEditDistance();
		
//		setupBasicProjection();
//		setupCrossJoins();
	}
	
	private void setupParse() {
//		expectedOutputs.put("parse1", "cross_join(project(filter(poe_med, i <= 5), poe_id, drug_type, dose_val_disp) AS a, project(filter(redimension(poe_med, <drug_type:string,drug_name:string,drug_name_generic:string,prod_strength:string,form_rx:string,dose_val_rx:string,dose_unit_rx:string,form_val_disp:string,form_unit_disp:string,dose_val_disp:double,dose_unit_disp:string,dose_range_override:string>[poe_id=0:10000000,1,1]), poe_id = 3750047), dose_val_rx) AS b)");
//		expectedOutputs.put("parse1", "sort(filter(poe_med, i <= 5), poe_id, drug_type, dose_val_disp)");
//		expectedOutputs.put("parse1", "apply(cross_join(project(filter(nation, n_name = 'brazil'), n_name) AS a, project(region, r_name) AS b, a.n_regionkey, b.r_regionkey), shifted_key, a.n_regionkey + 3)");
		
//		expectedOutputs.put("parse1", "filter(patients, id < 10)");
//		expectedOutputs.put("parse2", "cross_join(cross_join(cross_join(geo, filter(go_matrix, goid < 3), geo.geneid, go_matrix.geneid), filter(genes, id < 10), geo.geneid, genes.id), filter(patients, id <= 10 or id >= 30), geo.patientid, patients.id)");
//		expectedOutputs.put("parse2", "aggregate(cross_join(project(filter(nation, n_name = 'brazil'), n_name) AS a, project(region, r_name) AS b, a.n_regionkey, b.r_regionkey), count(n_name), count(r_name) as rcnt, n_nationkey, nation.n_regionkey)");
		expectedOutputs.put("parse2", "cross_join(cross_join(filter(region, region.r_name = 'AMERICA') as region_trimmed, nation, region_trimmed.r_regionkey, nation.n_regionkey), supplier as supplier_trimmed, nation.n_nationkey, supplier_trimmed.s_nationkey)");
		
//		expectedOutputs.put("planner1", "bdarray(redimension(cross_join(project(filter(nation, n_name = 'brazil'), n_name) AS a, project(region, r_name) AS b, a.n_regionkey, b.r_regionkey), <n_name:string> [n_nationkey=0:*,10,0,n_regionkey=0:*,3,0]));");
	}
	
	private void setupTreeEditDistance() {
		input1.put("tree1", "{cross_j{cross_j{c}{cross_j{a}{b}}}{d}}");
		input2.put("tree1", "{cross_j{cross_j{cross_j{a}{b}}{c}}{d}}");
	}
	
	
//	@Test
//	public void testParse1() throws Exception {
//		testParse("parse1");
//	}
//	
	@Test
	public void testParse2() throws Exception {
		testParse("parse2");
	}
//	
//	@Test
//	public void testPlanner() throws Exception {
//		testPlanner("planner1");
//	}
	
//	@Test
//	public void testTreeEdit() throws Exception {
//		testTreeEditDistance("tree1");
//	}
//	
//	private void setupBasicProjection() {
//		expectedOutputs.put("projection", "project(demography, demography.name, demography.id)");
//	}
//	
//	private void setupCrossJoins() {
//		expectedOutputs.put("cross_joins", "cross_join(cross_join(a, b), cross_join(c, d, c.id, d.id), a.id, c.id)");
//	}
//	
//	@Test
//	public void testBasicProjection() throws Exception {
//		testCase("projection");
//	}
//	
//	@Test
//	public void testCrossJoins() throws Exception {
//		testCase("cross_joins");
//	}
//	
//	
//	private void testCase(String testName) throws Exception {
//		
//		String expectedOutput = expectedOutputs.get(testName);
//		
//		Operator root = AFLParser.parsePlanTail(expectedOutput);
//		
//		System.out.println();
//		
//	}
	
	public void testTreeEditDistance(String testname) throws Exception {
		System.out.println("\nTree edit distnance: ");
		System.out.println("Tree1: "+input1.get(testname));
		System.out.println("Tree2: "+input2.get(testname));
		
	    BracketStringInputParser parser = new BracketStringInputParser();
	    Node<StringNodeData> t1 = parser.fromString(input1.get(testname));
	    Node<StringNodeData> t2 = parser.fromString(input2.get(testname));
	    // Initialise APTED.
	    APTED<StringUnitCostModel, StringNodeData> apted = new APTED<>(new StringUnitCostModel());
	    // Although we don't need TED value yet, TED must be computed before the
	    // mapping. This cast is safe due to unit cost.
	    apted.computeEditDistance(t1, t2);
	    // Get TED value corresponding to the computed mapping.
	    LinkedList<int[]> mapping = apted.computeEditMapping();
	    // This cast is safe due to unit cost.
		System.out.println("Distance: "+ apted.mappingCost(mapping));
	}
	
	
	public void testParse(String testname) throws Exception {
		
		AFLQueryPlan queryPlan = AFLPlanParser.extractDirect(new SciDBHandler((SciDBConnectionInfo)CatalogViewer.getConnectionInfo(6)), expectedOutputs.get(testname));
		Operator root = queryPlan.getRootNode();
		
		OperatorQueryGenerator gen = new AFLQueryGenerator();
		gen.configure(true, false);
		root.accept(gen);
		System.out.println(gen.generateStatementString()+"\n");
		
		System.out.println("Tree representation: "+root.getTreeRepresentation(true)+"\n");
		
		
	}
	
	public void testPlanner(String testname) throws Exception {
		String query = expectedOutputs.get(testname);
		
		try {
			Planner.processQuery(query, false);
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		
	} 
}
