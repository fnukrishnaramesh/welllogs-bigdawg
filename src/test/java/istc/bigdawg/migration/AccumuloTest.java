/**
 * 
 */
package istc.bigdawg.migration;

import static org.junit.Assert.assertEquals;

import java.util.Map.Entry;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableExistsException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.client.mock.MockInstance;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;
import org.jfree.util.Log;
import org.junit.Before;
import org.junit.Test;

import istc.bigdawg.LoggerSetup;
import istc.bigdawg.accumulo.AccumuloConnectionInfo;
import istc.bigdawg.accumulo.AccumuloInstance;
import istc.bigdawg.exceptions.AccumuloBigDawgException;

/**
 * Test basic data loading to Accumulo. Load a single row using BatchWriter.
 * 
 * @author Adam Dziedzic
 */
public class AccumuloTest {

	protected static final String AUTHORIZATION = "public";
	protected static final String COL_FAMILY = "mycolfamily";
	protected static final String TABLE = "testtable";
	protected static final String VALUE = "myvalue";
	protected static final String ROW = "row1";
	protected static final String COL_QUAL = "MyColQual";

	private static long accumuloBatchWriterMaxMemory = 50 * 1024 * 1024L;
	private static int accumuloBatchWriterMaxWriteThreads = 4;
	/**
	 * log
	 */
	private static Logger logger = Logger
			.getLogger(AccumuloTest.class.getName());

	@Before
	public void beforeTests() {
		LoggerSetup.setLogging();
	}

	protected static void recreateTable(AccumuloConnectionInfo conTo,
			String table) throws AccumuloSecurityException, AccumuloException {
		AccumuloInstance accInst = AccumuloInstance.getFullInstance(conTo);
		Connector connector = accInst.getConn();
		TableOperations tabOp = connector.tableOperations();
		recreateTable(tabOp, table);
	}

	protected static void recreateTable(TableOperations tabOp, String table) {
		try {
			try {
				tabOp.delete(table);
			} catch (TableNotFoundException e) {
				logger.debug("We have to drop the previous table " + table
						+ " and create a new one.");
				e.printStackTrace();
			}
			logger.debug("Create a new table " + table + " in Accumulo.");
			tabOp.create(table);
		} catch (TableExistsException e1) {
			logger.debug("Table exception, table creation failed.");
			e1.printStackTrace();
		} catch (AccumuloException e) {
			e.printStackTrace();
		} catch (AccumuloSecurityException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Load data to table using connection conn.
	 * 
	 * @param conn
	 * @param table
	 */
	protected static void loadData(Connector conn, String table) {
		try {
			BatchWriter writer;
			TableOperations tabOp = conn.tableOperations();
			recreateTable(tabOp, table);
			/* BatchWriterConfig has reasonable defaults. */
			BatchWriterConfig config = new BatchWriterConfig();
			/* Bytes available to batchwriter for buffering mutations. */
			config.setMaxMemory(accumuloBatchWriterMaxMemory);
			config.setMaxWriteThreads(accumuloBatchWriterMaxWriteThreads);
			writer = conn.createBatchWriter(table, config);

			Text rowID = new Text(ROW);
			Text colFam = new Text(COL_FAMILY);
			Text colQual = new Text(COL_QUAL);
			Value value = new Value(VALUE.getBytes());

			Mutation mutation = new Mutation(rowID);
			mutation.put(colFam, colQual, value);
			writer.addMutation(mutation);
			writer.flush();
			writer.close();
		} catch (TableNotFoundException e) {
			logger.error("Problem with creating BatchWriter, table not found.",
					e);
			e.printStackTrace();
		} catch (MutationsRejectedException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Test
	public void testMockInstance() {
		Instance instance = new MockInstance();
		// Instance instance = AccumuloInstance.getMiniCluster();
		TableOperations tabOp = null;
		try {
			Connector conn = instance.getConnector("root",
					new PasswordToken(""));
			tabOp = conn.tableOperations();
			loadData(conn, TABLE);
			Authorizations auths = new Authorizations(AUTHORIZATION);
			try {
				Scanner scan = conn.createScanner(TABLE, auths);
				scan.fetchColumnFamily(new Text(COL_FAMILY));

				for (Entry<Key, Value> entry : scan) {
					Key keyResultKey = entry.getKey();
					Text rowIdResult = entry.getKey().getRow();
					Text colFamResult = entry.getKey().getColumnFamily();
					Text colKeyResult = entry.getKey().getColumnQualifier();
					Value valueResult = entry.getValue();
					logger.debug("_Key_:" + keyResultKey + " _Row_:"
							+ rowIdResult + " _ColFam_:" + colFamResult
							+ " _ColQual_:" + colKeyResult + " _Value_:"
							+ valueResult);
					assertEquals(VALUE, valueResult.toString());
				}
			} catch (TableNotFoundException e) {
				logger.error("Table for scanner not found!", e);
				e.printStackTrace();
			}

		} catch (AccumuloException e) {
			e.printStackTrace();
		} catch (AccumuloSecurityException e) {
			e.printStackTrace();
		} finally {
			try {
				if (tabOp != null) {
					tabOp.delete(TABLE);
				}
			} catch (AccumuloException | AccumuloSecurityException
					| TableNotFoundException e) {
				Log.debug("Was not able to delete table: " + TABLE);
				e.printStackTrace();
			}
		}
	}

	@Test
	public void testMitInstance()
			throws AccumuloException, AccumuloSecurityException,
			AccumuloBigDawgException, TableNotFoundException {
		String table = "mytable";
		AccumuloInstance acc = AccumuloInstance.getInstance();
		long count = acc.countRows(table);
		logger.debug("Count rows in table " + table + ": " + count);
		assertEquals(1, count);
	}
}