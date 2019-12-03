package istc.bigdawg.signature.builder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import istc.bigdawg.catalog.CatalogViewer;
import istc.bigdawg.exceptions.BigDawgCatalogException;

public class ArraySignatureBuilder {
	
	private static Pattern opPattern  = null;
	private static Pattern objPattern = null;
	private static Pattern litPattern = null;
	
	public static void listing() throws IOException {
		
		// reading all the SQL commands
		BufferedReader bufferedReader = new BufferedReader(new FileReader("src/main/resources/SciDBParserTerms.csv"));
		StringBuffer opStringBuffer	  = new StringBuffer();
//		StringBuffer objStringBuffer  = new StringBuffer();
		String line 				  = bufferedReader.readLine();
		
		// get raw ops
		opStringBuffer.append("\\b"+line+"\\b");
		line = bufferedReader.readLine();
		do {
			 opStringBuffer.append("|\\b").append(line).append("\\b");
			line = bufferedReader.readLine();
		} while(line != null);
		
		// get tokens, so catalog can filter non-ops
//		objStringBuffer.append("(?i)(?!(").append(opStringBuffer).append("|\\bby\\b|\\bas\\b))\\b\\[\\w.]+\\b");
		
		// finish ops
		opStringBuffer.insert(0, "(?i)(").append(")");
		
		opPattern  = Pattern.compile(opStringBuffer.toString());
		objPattern = Pattern.compile("[\\-\\_@a-zA-Z0-9.]+");
		litPattern = Pattern.compile("(<[^>]*>[^\\[]*[^\\]]*\\])|((?<!([a-zA-Z_]{1,10}[0-9.]{0,10}))(([0-9]*[.]?[0-9]+)))|([-][ ]*[0-9]*[.]?[0-9]+)|'[^']*'|(?i)(\\bnull\\b|\\btrue\\b|\\bfalse\\b)");
		bufferedReader.close();
	}
	
	public static String sig1(String input) throws Exception {
		if (opPattern == null) listing();
		
		StringBuffer stringBuffer	= new StringBuffer();
		Matcher matcher				= opPattern.matcher(input);
		
		try {
			matcher.find();
			stringBuffer.append(input.substring(matcher.start(), matcher.end()));
			while (matcher.find()) {
				stringBuffer.append("\t").append(input.substring(matcher.start(), matcher.end()));
			}
			return stringBuffer.toString();
			
		} catch (IllegalStateException e) {
			return "";
		}
	}

	public static List<String> sig2(String input) throws IOException, BigDawgCatalogException, SQLException {
		if (objPattern == null) listing();
		
		StringBuffer stringBuffer	= new StringBuffer();
		Matcher matcher				= objPattern.matcher(input);
		StringBuffer dawgtags  		= new StringBuffer();
		Pattern tagPattern			= Pattern.compile("BIGDAWGTAG_[0-9_]+");
		Matcher tagMatcher			= tagPattern.matcher(input);
		
		try {
			matcher.find();
			stringBuffer.append(input.substring(matcher.start(), matcher.end()));
			while (matcher.find()) {
				stringBuffer.append(",").append(input.substring(matcher.start(), matcher.end()));
			}
			if (tagMatcher.find())
				dawgtags.append(input.substring(tagMatcher.start(), tagMatcher.end()));
			while (tagMatcher.find())
				dawgtags.append("\t"+input.substring(tagMatcher.start(), tagMatcher.end()));
		} catch (IllegalStateException e) {
			return new ArrayList<>();
		}
		
		String result = CatalogViewer.getObjectsFromList(stringBuffer.toString());
		if (result.length() == 0) {
			return Arrays.asList(dawgtags.toString().split("\t"));
		} else {
			return Arrays.asList(result.concat("\t"+dawgtags.toString()).split("\t"));
		}
	}

	/**
	 * Signature 3, constants. Schema definition excluded.
	 * @param input
	 * @return TSV String of constants, including string, integer and decimal numbers, true and false, and null
	 * @throws IOException 
	 * @throws Exception
	 */
	public static List<String> sig3(String input) throws IOException   {
		if (litPattern == null) listing();
		
		StringBuffer stringBuffer	= new StringBuffer();
		Matcher matcher				= litPattern.matcher(input);
		
		try {
			while (matcher.find()) {
				if (input.charAt(matcher.start()) != '<') {
					if (stringBuffer.length() > 0)
						stringBuffer.append("\t").append(input.substring(matcher.start(), matcher.end()));
					else stringBuffer.append(input.substring(matcher.start(), matcher.end()));
				}
			}	
			return Arrays.asList(stringBuffer.toString().replace(" ", "").split("\t"));
			
		} catch (IllegalStateException e) {
			return new ArrayList<>();
		}
	}
}
