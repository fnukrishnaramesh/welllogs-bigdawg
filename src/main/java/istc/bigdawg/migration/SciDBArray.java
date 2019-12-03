/**
 * 
 */
package istc.bigdawg.migration;

import java.io.Serializable;

/**
 * Information about an array for the migration process.
 * 
 * @author Adam Dziedzic
 */
public class SciDBArray implements Serializable {

	/**
	 * Serial version of the class (for serialization).
	 */
	private static final long serialVersionUID = -4144860433440893335L;

	/** The name of the array. */
	private String name;

	/**
	 * Was it created during the migration, or was there before the migration.
	 */
	private boolean wasCreated;

	/**
	 * Is the array created only for the migration (a flat intermediate array).
	 */
	private boolean isItermediate;

	public SciDBArray(String name, boolean wasCreated, boolean isItermediate) {
		this.name = name;
		this.wasCreated = wasCreated;
		this.isItermediate = isItermediate;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the wasCreated
	 */
	public boolean wasCreated() {
		return wasCreated;
	}

	/**
	 * @return the isItermediate
	 */
	public boolean isItermediate() {
		return isItermediate;
	}

}
