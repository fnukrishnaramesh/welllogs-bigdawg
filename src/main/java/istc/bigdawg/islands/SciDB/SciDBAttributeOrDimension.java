package istc.bigdawg.islands.SciDB;

import java.util.HashSet;
import java.util.Set;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

public class SciDBAttributeOrDimension {

	protected String name = null;
	protected Set<SciDBAttributeOrDimension> sources = null; //  the provenance of each DataObjectAttribute, map to prevent duplicates
	protected SciDBArray srcDataObject = null;
	protected String typeString = null;
	protected boolean hidden = false;
	protected String expression = null;
	
	public SciDBAttributeOrDimension(String n) {
		name = new String(n);
		// sources left null;
		// srcDataObject left null;
	}
	
	public SciDBAttributeOrDimension(SciDBArray o, String n) throws JSQLParserException {
		this(n);
		srcDataObject = new SciDBArray(o);
	}
	
	public SciDBAttributeOrDimension(SciDBAttributeOrDimension sa) throws JSQLParserException {
		this.name = new String(sa.name);
		if (sa.srcDataObject != null)
			this.srcDataObject = new SciDBArray(sa.srcDataObject);
		
		
		if (sa.sources != null) {
			this.sources = new HashSet<>();
			for (SciDBAttributeOrDimension a : sa.sources) {
				this.sources.add(new SciDBAttributeOrDimension(a));
			}
		}
		
		if (sa.expression != null)
			this.expression = new String(sa.expression.toString());
	}


	public SciDBAttributeOrDimension() {}

	
	public void copy(SciDBAttributeOrDimension r) throws JSQLParserException {
		this.name = r.name;
		this.srcDataObject = new SciDBArray(r.srcDataObject);
		this.hidden = r.hidden;
		this.typeString = new String(r.typeString);
		this.sources = null;
		if (r.expression != null)
			this.expression = new String(r.expression.toString());
		
		addSourceAttribute(r);

	}
	
	public void addSourceAttribute(SciDBAttributeOrDimension s) {
		if(sources == null) {
			sources = new HashSet<SciDBAttributeOrDimension>();
		}
		
		if(s.getSourceAttributes() == null) {
			sources.add(s);
		}
		else {
			sources.addAll(s.getSourceAttributes());
		}
	}
	
	public Set<SciDBAttributeOrDimension> getSourceAttributes() {
		return sources;
	}
	
	public  String getName() {
		return name;
	}
	
	
	public void setName(String n) {
		name = n;
	}
	
	public SciDBArray getDataObject() {
		return srcDataObject;
	}
	
	public void setDataObject(SciDBArray o) {
		srcDataObject = o;
	}
	
	public String getFullyQualifiedName() {
		if(srcDataObject != null) {
			return srcDataObject.getFullyQualifiedName() + "." + name;
		}
		else return name;
	}
	
	public void setTypeString (String t) {
		typeString = new String(t);
	}
	
	public String getTypeString () {
		return typeString;
	}
	
	public boolean isHidden() {
		return hidden;
	}
	
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	
//	public String generateSQLTypedString() {
////		return name.replaceAll(".+\\.(?=[\\w]+$)", "___") + " " + convertTypeStringToSQLTyped();
//		return name.replaceAll(".+\\.(?=[\\w]+$)", "") + " " + convertTypeStringToSQLTyped();
//	}
	
//	public String generateAFLTypeString() {
//		
//		char token = ':';
//		if (isHidden())
//			token = '=';
//		
//		return name.replaceAll(".+\\.(?=[\\w]+$)", "") + token + convertTypeStringToAFLTyped();
//		
//	}
	
//	public String convertTypeStringToSQLTyped() {
//		
//		if (typeString == null || typeString.charAt(0) == '*' || (typeString.charAt(0) >= '0' && typeString.charAt(0) <= '9'))
//			return "integer";
//		
//		String str = typeString.concat("     ").substring(0,5).toLowerCase();
//		
//		switch (str) {
//		case "int32":
//		case "int64":
//			return "integer";
//		case "string":
//			return "varchar";
//		case "float":
//			return "double precision";
//		case "bool ":
//			return "boolean";
//		default:
//			return typeString;
//		}
//		
//	}
	
//	public String convertTypeStringToAFLTyped() {
//		
//		if (typeString == null) {
//			System.out.println("Missing typeString: "+ this.name);
//			return "int64";
//		}
//		
//		if (typeString.charAt(0) == '*' || (typeString.charAt(0) >= '0' && typeString.charAt(0) <= '9'))
//			return typeString;
//		
//		String str = typeString.concat("     ").substring(0,5).toLowerCase();
//		
//		switch (str) {
//		
//		case "varch":
//			return "string";
//		case "times":
//			return "datetime";
//		case "doubl":
//			return "double";
//		case "integ":
//		case "bigin":
//			return "int64";
//		case "boole":
//			return "bool";
//		default:
//			return typeString;
//		}
//	}
	
	public Expression getSQLExpression() {
		try {
			return CCJSqlParserUtil.parseExpression(expression);
		} catch (JSQLParserException e) {
			return null;
		}
	}
	
	public void setExpression(Expression expr) {
		if (expr != null)
			this.expression= new String (expr.toString());
	}
	
	public void setExpression(String exprString) {
		if (exprString != null)
			this.expression= new String (exprString);
	}
	
	public String getExpressionString() {
		if (expression == null) return null;
		return expression.toString();
	}
	
	
	
	@Override
	public String toString() {
		
		String ret = new String(this.getFullyQualifiedName() + ": ");
		
		if(typeString != null) {
			ret += "type: " +  typeString;
		}
		
		ret += "; Expression: " + ( expression == null ? null : expression.toString());
		
		return ret;
		
	}
}
