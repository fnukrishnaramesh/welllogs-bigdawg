/**
 * 
 */
package istc.bigdawg.system;

import static istc.bigdawg.utils.RunShell.runShell;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import istc.bigdawg.LoggerSetup;
import istc.bigdawg.exceptions.RunShellException;
import istc.bigdawg.properties.BigDawgConfigProperties;
import istc.bigdawg.utils.Constants;
import istc.bigdawg.utils.StackTrace;

/**
 * Test the external interface of BigDAWG.
 * 
 * Run the queries from CURL and compare the obtained result with the expected
 * one.
 * 
 * @author Adam Dziedzic
 */
public class CurlTest {

	/**
	 * log
	 */
	private static Logger log = Logger.getLogger(CurlTest.class.getName());

	/* How many seconds should wait for BigDAWG main thread to start. */
	private static int WAIT_TIME = 3;

	/* The main thread of BigDAWG that is running during the system tests. */
	private static MainTask mainTask = null;

	/* Curl command line input. */
	private final static String CURL = "curl";
	private final static String X = "-X";
	private final static String POST = "POST";
	private final static String D = "-d";

	private final static String TRUTH_PATH = "src/test/resources/SystemTests/";

	/* Path to the curl to issue the BigDAWG query. */
	private final static String PATH = BigDawgConfigProperties.INSTANCE
			.getBaseURI() + "query/";

	/* Queries and "ground-truth" file with correct answers. */

	/* Query from a single PostgreSQL's instance - for patients. */
	private static final String RELATIONAL_PATIENTS = "'bdrel(select subject_id, "
			+ "sex, dob from mimic2v26.d_patients order by subject_id limit 5;)'";
	/*
	 * The name of the file where the correct result for the query for patients
	 * is stored.
	 */
	private static final String PATIENTS_FILE = "d_patients.txt";

	/*
	 * Query spanning two instance of PostgreSQL.
	 * 
	 */
	private static final String POSTGRES_POSTGRES_QUERY = "'bdrel(select hadm_id, mimic2v26.admissions.subject_id, icustay_id, itemid, ioitemid, charttime, elemid from mimic2v26.additives, mimic2v26.admissions where mimic2v26.additives.subject_id = mimic2v26.admissions.subject_id order by hadm_id, mimic2v26.admissions.subject_id, icustay_id, itemid, ioitemid, charttime, elemid limit 10;)'";
	private static final String POSTGGRES_POSTGRES_FILE = "postgres_postgres_migration.txt";

	/*
	 * Query going from PostgreSQL to SciDB.
	 * 
	 */
	private static final String POSTGRES_SCIDB_QUERY = "'bdarray(scan(bdcast(bdrel(SELECT poe_id, subject_id FROM mimic2v26.poe_order order by poe_id, subject_id LIMIT 5), poe_order_copy, '<subject_id:int32>[poe_id=0:*,100000,0]', array)));'";
	private static final String POSTGGRES_SCIDB_FILE = "postgres_scidb_cast.txt";

	/**
	 * Initialize the curl queries. Run before any tests in this class.
	 */
	@Before
	public void setUp() {
		log.debug("Test the CURL queries.");
		mainTask = new MainTask();
		log.debug(String.format("Allow %d seconds for BigDAWG to start.",
				WAIT_TIME));
		try {
			TimeUnit.SECONDS.sleep(WAIT_TIME);
		} catch (InterruptedException e) {
			fail("Interruption of the wait in CurlTest should not happen: "
					+ e.getMessage());
		}
	}

	/*
	 * Close all the resources and clean the environment. Run after all the
	 * tests were done.
	 */
	@After
	public void tearDown() {
		mainTask.close();
		log.debug("Finished testing the CURL queries.");
	}

	/**
	 * Construct the class.
	 */
	public CurlTest() {

	}

	@Test
	/**
	 * Test the query for patients. This runs the query in the relational
	 * island.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RunShellException
	 */
	public void testRelationalPatients()
			throws IOException, InterruptedException, RunShellException {
		compareOrdered(RELATIONAL_PATIENTS, PATIENTS_FILE);
	}

	@Test
	/**
	 * Test the query which migrates data between two instances of PostgreSQL.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RunShellException
	 */
	public void testPostgresPostgres()
			throws IOException, InterruptedException, RunShellException {
		compareOrdered(POSTGRES_POSTGRES_QUERY, POSTGGRES_POSTGRES_FILE);
	}

	@Test
	/**
	 * Test the query which casts data between PostgreSQL and SciDB.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RunShellException
	 */
	public void testPostgresSciDB()
			throws IOException, InterruptedException, RunShellException {
		compareOrdered(POSTGRES_SCIDB_QUERY, POSTGGRES_SCIDB_FILE);
	}

	/**
	 * Compare results given by the query (from CURL) with the correct results
	 * stored in the file.
	 * 
	 * @param query
	 *            The query to be run via CURL.
	 * @param truthFile
	 *            The correct results stored in a file.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RunShellException
	 */
	private void compareOrdered(String query, String truthFile)
			throws IOException, InterruptedException, RunShellException {
		InputStream curl = runCurl(query);
		BufferedReader curlReader = new BufferedReader(
				new InputStreamReader(curl));

		BufferedReader fileReader = new BufferedReader(
				new FileReader(TRUTH_PATH + truthFile));

		compareOrdered(curlReader, fileReader, query);
	}

	/**
	 * Change the array of string values into java Array and trim the white
	 * characters in front and at the end of each string/word.
	 * 
	 * @param values
	 *            - a standard array of String values.
	 * 
	 * @return java Array of the trimmed strings
	 */
	private static List<String> getArray(String[] values) {
		List<String> lineValues = new ArrayList<String>();
		for (String value : values) {
			lineValues.add(value.trim());
		}
		return lineValues;
	}

	/**
	 * Compared ordered output extracted from two readers.
	 * 
	 * @param curlReader
	 *            the output from the CURL command
	 * @param fileReader
	 *            the "ground-truth" from the resource file
	 * @param query
	 *            the query which was run in CURL
	 */
	public void compareOrdered(BufferedReader curlReader,
			BufferedReader fileReader, String query) {
		try {
			String lineCurl = curlReader.readLine();
			String lineFile = fileReader.readLine();

			while (lineCurl != null && lineFile != null) {
				log.debug("line curl: " + lineCurl);
				log.debug("line file: " + lineFile);
				String[] curlStrings = lineCurl.split("\\s+");
				List<String> curlArray = getArray(curlStrings);
				String[] fileStrings = lineFile.split("\\s+");
				List<String> fileArray = getArray(fileStrings);
				assertEquals("The expected row: " + lineFile
						+ " differs from the row returned by CURL: " + lineCurl,
						fileArray, curlArray);
				lineCurl = curlReader.readLine();
				lineFile = fileReader.readLine();
			}
			assertTrue(
					"The CURL query: " + query
							+ " returned more results than expected!",
					lineCurl == null);
			assertTrue(
					"The CURL query: " + query
							+ " returned fewer results than expected!",
					lineFile == null);
		} catch (IOException ex) {
			fail("This problem: " + ex.getMessage() + " should not happen!");
		}
	}

	/**
	 * Run curl command as it was from the command line.
	 * 
	 * @param query:
	 *            the query that should be passed to the curl command.
	 * 
	 * @return InputStream of the data from the command.
	 */
	public static InputStream runCurl(String query)
			throws IOException, InterruptedException, RunShellException {
		try {
			String[] command = { CURL, X, POST, D, query, PATH };
			return runShell(new ProcessBuilder(command));
		} catch (RunShellException e) {
			String msg = "Running the CURL command from the shell with query: "
					+ query + " throws the exception: " + e.getMessage();
			log.error(msg + " " + StackTrace.getFullStackTrace(e), e);
			throw new RunShellException(msg);
		}
	}

	/**
	 * Read lines from the given reader.
	 * 
	 * @param reader
	 *            - the stream of lines.
	 * 
	 * @returns a set of lines (each line consists of list of strings).
	 */
	public static Set<List<String>> getLines(BufferedReader reader)
			throws IOException {
		List<String> lines = new ArrayList<>();
		String line;
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}
		return getLineSet(lines);
	}

	/**
	 * Read lines from the given file/pipe.
	 * 
	 * @param filePath:
	 *            full path to a file or pipe from which we read data line by
	 *            line.
	 * 
	 * @returns a set of lines (each line consists of list of strings).
	 */
	public static Set<List<String>> getLines(String filePath)
			throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(filePath),
				Charset.forName(Constants.ENCODING));
		return getLineSet(lines);
	}

	/**
	 * Get set of list of words.
	 * 
	 * @param lines
	 *            The List of lines.
	 * @return a set of lines (each consisting of list of words/strings).
	 */
	private static Set<List<String>> getLineSet(List<String> lines) {
		Set<List<String>> lineSet = new HashSet<List<String>>();
		for (String line : lines) {
			/*
			 * Split lines based on 1 or more white characters (previously it
			 * was based only on tab).
			 */
			String[] values = line.split("\\s+");
			List<String> lineValues = getArray(values);
			lineSet.add(lineValues);
		}
		return lineSet;
	}

	/**
	 * @param args
	 * @throws RunShellException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void main(String[] args)
			throws IOException, InterruptedException, RunShellException {
		LoggerSetup.setLogging();
		log.debug("Run the main() in CurlTest.");
		CurlTest test = new CurlTest();
		test.setUp();
		log.debug("The path is: " + PATH);
		InputStream inStream = null;
		String result = null;

		try {
			inStream = runCurl(RELATIONAL_PATIENTS);
		} catch (IOException | InterruptedException | RunShellException e1) {
			log.error("Problem with running curl.");
			e1.printStackTrace();
			System.exit(1);
		}
		try {
			result = IOUtils.toString(inStream, Constants.ENCODING);
		} catch (IOException e) {
			log.error("Problem with reading result from the input stream.");
			e.printStackTrace();
			System.exit(1);
		}
		log.debug(result);
		test.tearDown();
	}

}
