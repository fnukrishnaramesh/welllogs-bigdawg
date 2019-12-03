/**
 * 
 */
package istc.bigdawg.scidb;

import istc.bigdawg.utils.Tuple.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Adam Dziedzic
 *
 */
public class ParseSciDBResponse {

	/**
	 * 
	 */
	private ParseSciDBResponse() {
	}
	
	public static Tuple2<List<String>, List<List<String>>> parse(String sciDBResponse) {
		String csvSeparator="\t";
		String[] lines = sciDBResponse.split(System.getProperty("line.separator"));
		String[] colNamesRaw=lines[0].split(csvSeparator);
		List<String> colNames = Arrays.asList(colNamesRaw);
		List<List<String>> tuples = new ArrayList<List<String>>();
		for (int i=1; i<lines.length;++i) {
			String line = lines[i];
			List<String> tuple = Arrays.asList(line.split(csvSeparator));
			tuples.add(tuple);
		}
		return new Tuple2<List<String>, List<List<String>>>(colNames, tuples);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String response = "name	uaid	aid	schema	availability	temporary\n"+
				"ABP_0	510	510	ABP_0<obs:double> [timestamp=0:*,1250000,0]	true	false\n"+
				"ABP_0_20000_flat	505	505	ABP_0_20000_flat<timestamp:int64,obs:double> [i=0:*,1000000,0]	true	false\n"+
				"ABP_s00124_wave_325553800032	449	449	ABP_s00124_wave_325553800032<obs:double> [timestamp=0:*,1250000,0]	true	false\n"+
				"ABP_s01158_wave_306308400011	410	410	ABP_s01158_wave_306308400011<obs:double> [timestamp=0:*,1250000,0]	true	false\n"+
				"ABP_s03386_wave_398294300061	413	413	ABP_s03386_wave_398294300061<obs:double> [timestamp=0:*,1250000,0]	true	false";

		System.out.println("example response: "+response);
		Tuple2<List<String>,List<List<String>>> parsedData = parse(response);
		List<String> colNames = parsedData.getT1();
		System.out.print("Number of columns: "+colNames.size());
		System.out.println("colNames: "+colNames);
		List<List<String>> tuples = parsedData.getT2();
		for (List<String> tuple: tuples) {
			System.out.print("number of values: "+tuple.size()+";");
			System.out.println(tuple);
		}
	}

}
