package istc.bigdawg.islands.relational;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import istc.bigdawg.exceptions.QueryParsingException;
import istc.bigdawg.islands.relational.utils.SQLAttribute;
import istc.bigdawg.islands.relational.utils.SQLExpressionUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;

//public class SQLOutItemResolver extends CommonOutItemResolver {
public class SQLOutItemResolver {

	// takes in the content of a <Item> field in EXPLAIN xml 
	// keeps track of fields referenced in expression for security level 
	// and any function / aggregates referenced
	
	// supports:
	// column references
	// math expression
	// aggregate 
	// windowed aggregate
	
	// TODO: expressions with multiple aggregates might not work
	
	private List<Function> aggregates;
	private List<AnalyticExpression> windowedAggregates;
	
//	private List<String> commonAggregates;
//	private List<String> commonWindowedAggregates;
	private String alias = null; // attr alias
	private SQLAttribute outAttribute;
	
	public SQLOutItemResolver(String expr,  Map<String, SQLAttribute> srcSchema, 
			SQLTableExpression supplement) throws QueryParsingException, JSQLParserException {
		super();
		
		String typeStr = null;
		if (expr.indexOf("::") >= 0) {
			typeStr = expr.substring(expr.lastIndexOf("::")+2).replaceAll("[)]", "");
		} else {
			String finder = expr;
			finder = finder.replaceAll("::[ \\w]+", "");
			
			SQLAttribute doa = srcSchema.get(finder);
			while (doa == null) {
				
				String before = new String(finder);
				finder = finder.replaceAll("^([_@a-zA-Z0-9]+\\.)", "");
				
				if (before.equals(finder)) {
					// shaving the front doesn't work, it's probably a function or other type of expression
					List<Column> resultStrings = SQLExpressionUtils.getAttributes(CCJSqlParserUtil.parseExpression(finder));
					if (resultStrings.size() > 0)
						finder = resultStrings.get(0).getFullyQualifiedName();
					else 
						break;
					if (finder.equals(before)) {
						for (String s : srcSchema.keySet()) {
							if (finder.equals(srcSchema.get(s).getExpressionString()) || expr.equals(srcSchema.get(s).getExpressionString())) {
								doa = srcSchema.get(s); 
								break;
							}
						}	
					
						if (doa != null) break;
						
						// else not found
						System.out.println("cannot find: "+expr+"; finder: "+finder+"; srcSchema: ");
						for (String s : srcSchema.keySet()) {
							System.out.println("-- "+s+"; "+srcSchema.get(s).getSQLExpression());
						}
						throw new QueryParsingException("cannot find: "+expr+"; finder: "+finder+"; srcSchema: "+srcSchema.toString());
					}
				}
				doa = srcSchema.get(finder);
			}
			if (doa != null)
				typeStr = doa.getTypeString();
		}
		
		outAttribute = new SQLAttribute();
		if (typeStr != null)
			outAttribute.setTypeString(typeStr);
		else {
			// so far we've only observed count(*) causing trouble. Take note:
			outAttribute.setTypeString("integer");
		}
		
		
		aggregates = new ArrayList<Function>();
		windowedAggregates = new ArrayList<AnalyticExpression>();

		
		
		// get rid of any psql param placeholders
		expr = expr.replace("?", " ");
		
		expr = SQLExpressionUtils.removeExpressionDataTypeArtifactAndConvertLike(expr);
		
		if(supplement != null) {
			alias = supplement.getAlias(expr);
			if (alias == null) {
				if (SQLJSONPlaceholderParser.possiblyContainsOperator(expr)) {
					expr = SQLJSONPlaceholderParser.transformJSONQuery(expr);
				}
				Expression expres = CCJSqlParserUtil.parseExpression(expr);
				//				System.out.println("expr: "+expr+"\nsupplement: ");
				//				for (String s : supplement.getAliases().keySet()){
				//					System.out.println("-- "+s+"; "+supplement.getAlias(s));
				//				}
				SQLExpressionUtils.removeExcessiveParentheses(expres);
				expr = expres.toString();
				alias = supplement.getAlias(expr);
			}
		}
		
		outAttribute.setExpression(expr);
		
		
		// if plan is fully qualified (i.e., has joins)
		// then we propagate the full names to any aliases
		// aliases are original values to new values
		String firstAttrName = srcSchema.keySet().iterator().next();
		Map<String, String> aliases = new HashMap<String, String>();
		
		if(supplement != null) {	
			aliases = supplement.getAliases();
		}
		// table alias resolution
		if(alias == null && firstAttrName.contains(".")) {
			// in fully qualified mode
			// correct any aliases that are in local mode

			for(String s : aliases.keySet()) {
				String sprime = fullyQualify(s, srcSchema);
				if(sprime.equalsIgnoreCase(expr)) {
					alias = aliases.get(s);
				}
			}
		}
		

		// there's no alias
		if(alias == null) {
			
			String after = expr.replaceAll("[(][*_.'\\w]+[)]", "");
			if (expr.equals(after) && !expr.contains("(")) {
				alias = expr;
			} else if (expr.contains("(")) {
				for (String key : srcSchema.keySet()) {
					
					if (srcSchema.get(key).getExpressionString().contains(expr)) {
						alias = key;
						break;
					}
				}
				if (alias == null) {
					alias = after.replaceAll("[*_\\-+/\\. (),'=]", "").replaceAll("^[0-9]+", "");
					Random rand = new Random();
					int randomNum = rand.nextInt(200);
					alias = alias.substring(0, (20 < alias.length() ? 20 : alias.length())) + randomNum;
				}
			} else {
				alias = after.replaceAll("[*_\\-+/\\. (),'=]", "").replaceAll("^[0-9]+", "");
				Random rand = new Random();
				int randomNum = rand.nextInt(200);
				alias = alias.substring(0, (20 < alias.length() ? 20 : alias.length()))+randomNum;
			}
		} 
		
		outAttribute.setName(alias);
		((SQLAttribute)outAttribute).setType(null);
		
		ExpressionDeParser deparser = new ExpressionDeParser() {
		
			
			public void visit(Column tableColumn) {
				super.visit(tableColumn);

				String name = tableColumn.getColumnName();	
				SQLAttribute lookup = (SQLAttribute)srcSchema.get(name);
				
				// try fully qualified name
				if(lookup == null) {
					name = tableColumn.getFullyQualifiedName();
					lookup = (SQLAttribute) srcSchema.get(name);					
				}
				
				if (lookup == null){
					
					for (SQLAttribute doa : srcSchema.values()) {
						try {
							if (doa.getSQLExpression() != null) {
								
								List<Column> lc = SQLExpressionUtils.getAttributes(doa.getSQLExpression());
								List<String> ls = new ArrayList<>();
								for (Column c : lc) ls.add(c.toString());
								if (!ls.contains(name)) continue;
								
								for (SQLAttribute doa2: doa.getSourceAttributes()) {
									if (name.contains(doa2.getName())){
										lookup = doa2;
										break;
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (lookup != null)
							break;
					}
				}
					
				if (lookup == null)  {
					System.out.println("(SQLAttribute) srcSchema.get(name), name = "+tableColumn.getFullyQualifiedName() + "; alias: "+alias);
					
					System.out.println("srcSchema: ");
					for (String s : srcSchema.keySet()) {
						System.out.println("-- "+s+"; "+srcSchema.get(s).getSQLExpression());
					}
				}
				
				outAttribute.addSourceAttribute(lookup);
				
				// first column that is in this expression
				if(((SQLAttribute)outAttribute).getType() == null) {
					((SQLAttribute)outAttribute).setType(lookup.getType());
				}
				else {
					// check to make sure it is the same type
					assert(((SQLAttribute)outAttribute).getType().getDataType() == lookup.getType().getDataType());
				}
			}
			
			// simple aggregates
			public void visit(Function function) { 
				super.visit(function);
				aggregates.add(function);
				if(function.isAllColumns()) {
					// find attribute with highest security attribute in src schema
					// must be count(*), all others don't support this
					   setUpAggregateAllColumns(srcSchema, ((SQLAttribute)outAttribute));
					}   // else (not *)  delegate got column visitor above
			}
		
			// windowed aggregate
			public void visit(AnalyticExpression aexpr) {
				super.visit(aexpr);
				// grab aexpr from supplement
				
				AnalyticExpression fullExpression = supplement.getAnalyticExpression();
				windowedAggregates.add(fullExpression);
				assert(aexpr.getName() == "row_number"); // all others not yet implemented
				setUpAggregateAllColumns(srcSchema, ((SQLAttribute)outAttribute));  // TODO: make this more fine grained, only derived from ORDER BY, PARTITION BY and possibly aggregate
				outAttribute.setExpression(fullExpression);
				
			}
			

		
		
		}; // end expression parser

		Expression parseExpression = CCJSqlParserUtil.parseExpression(expr);
		((SQLAttribute)outAttribute).setExpression(parseExpression);

		StringBuilder b = new StringBuilder();
		deparser.setBuffer(b);
		parseExpression.accept(deparser); // adjusts outAttribute for winagg case
	}
	
	// takes in alias src, determines if it has a match in src schema
	// if so, it prefixes the column reference with the src table
	public String fullyQualify(String expr, Map<String, SQLAttribute> srcSchema) throws JSQLParserException {
		
		ExpressionDeParser deparser = new ExpressionDeParser() {
		
			
			public void visit(Column tableColumn) {
				
				if(tableColumn.getTable().getName() == null) {
					
					for(String s : srcSchema.keySet()) {
						final String[] names = s.split("\\.");

						if (names.length > 1 && tableColumn.getColumnName().equalsIgnoreCase(names[1])) { // happens just once
							final Table t = new Table(names[0]);
							tableColumn.setTable(t);
						} 
					}
										
				}
				
				super.visit(tableColumn);
			}
			
		
		}; // end expression parser
		
		  StringBuilder b = new StringBuilder();
		  deparser.setBuffer(b);
  		  Expression parseExpression = CCJSqlParserUtil.parseExpression(expr);
		  parseExpression.accept(deparser);
		  return b.toString();

	}
		

	
	
	static void setUpAggregateAllColumns(Map<String, SQLAttribute> srcSchema, SQLAttribute out) {
		ColDataType intAttrType = new ColDataType();
		intAttrType.setDataType("integer");
		out.setType(intAttrType);
		
		for(SQLAttribute src : srcSchema.values()) {
			out.addSourceAttribute(src);
		}
		
		
	}
	
	public SQLAttribute getAttribute() {
		return ((SQLAttribute)outAttribute);
	}
	
	public boolean hasAggregate() {
		return !aggregates.isEmpty();
	}
	
	public boolean hasWindowedAggregates() {
		return !windowedAggregates.isEmpty();
	}
	
	public List<Function> getAggregates() {
		return aggregates;
	}
	
	public List<AnalyticExpression> getWindowedAggregates() {
		return windowedAggregates;
	}
}
