/**
 * 
 */
package istc.bigdawg.query;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Collection;

import istc.bigdawg.executor.ExecutorEngine;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class represents a connection information to a given database.
 * 
 * @author Adam Dziedzic
 */
public interface ConnectionInfo extends Serializable {
    
    /**
     * 
     * @return name of the URL where the instance is running from which the
     *         connection will be obtained
     */
    public String getUrl();

	/**
	 * 
	 * @return name of the host where the instance is running from which the
	 *         connection will be obtained
	 */
	public String getHost();

	/**
	 * 
	 * @return port number of the running instance from which the connection
	 *         will be obtained
	 */
	public String getPort();

	/**
	 * 
	 * @return user name who can can get the connection
	 */
	public String getUser();

	/**
	 * 
	 * @return password for the user
	 */
	public String getPassword();

	/**
	 *
	 * @return databases/binpath, etc
	 */
	public String getDatabase();

	/**
	 * @param objects
	 *            a Collection of objects to be removed
	 * @return a collection of queries that when run on the instance, removes
	 *         all of the given objects.
	 */
	public Collection<String> getCleanupQuery(Collection<String> objects);

	/**
	 * Used for executing shuffle joins.
	 * @param object
	 *            the table for which the histogram should be computed
	 * @param attribute
	 *            the attribute of the table for which the histogram should be
	 *            computed
	 * @param start
	 *            the minimum value contained in the histogram
	 * @param end
	 *            the maximum value contained in the histogram
	 * @param numBuckets
	 *            the number of buckets in the histogram
	 * @return an array such that the ith value is equal to the number of
	 *         elements stored in the ith bucket of the histogram
	 */
	public long[] computeHistogram(String object, String attribute,
			double start, double end, int numBuckets)
					throws ExecutorEngine.LocalQueryExecutionException;

	/**
	 * Used for executing shuffle joins.
	 * @param object
	 * @param attribute
	 * @return
	 * @throws ExecutorEngine.LocalQueryExecutionException
	 * @throws ParseException
	 */
	public Pair<Number, Number> getMinMax(String object, String attribute)
			throws ExecutorEngine.LocalQueryExecutionException, ParseException;

	public ExecutorEngine getLocalQueryExecutor()
			throws LocalQueryExecutorLookupException;

	class LocalQueryExecutorLookupException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public LocalQueryExecutorLookupException() {
			super();
		}

		public LocalQueryExecutorLookupException(String message) {
			super(message);
		}

		public LocalQueryExecutorLookupException(Throwable cause) {
			super(cause);
		}

		public LocalQueryExecutorLookupException(String message,
				Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * @return
	 */
	public default String toSimpleString() {
		StringBuilder result = new StringBuilder();

		result.append(this.getClass().getName() + " Object {");
		result.append(" Host: " + this.getHost());
		result.append(" Port: " + this.getPort());
		result.append(" Database: " + this.getDatabase());
		result.append(" User: " + this.getUser());
		result.append(" Password: This is a secret!");
		result.append("}");

		return result.toString();
	}
}
