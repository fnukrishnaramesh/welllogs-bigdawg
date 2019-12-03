package istc.bigdawg.catalog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import istc.bigdawg.exceptions.BigDawgCatalogException;
import istc.bigdawg.exceptions.BigDawgException;
import istc.bigdawg.exceptions.UnsupportedIslandException;
import istc.bigdawg.islands.IslandAndCastResolver;
import istc.bigdawg.islands.IslandAndCastResolver.Engine;
import istc.bigdawg.islands.IslandAndCastResolver.Scope;
import istc.bigdawg.query.ConnectionInfo;
import org.apache.avro.generic.GenericData;

public class CatalogViewer {

	public static Engine getEngineOfDB(int dbid) throws BigDawgCatalogException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		// NEW ADDITION
		ResultSet rs1 = null;
		try {
			rs1 = cc.execRet("select connection_properties from catalog.databases db join catalog.engines e on db.engine_id = e.eid where dbid = "+dbid);
		
			if (rs1.next()) {
				String engineString = rs1.getString("connection_properties");
				try {
					return IslandAndCastResolver.getEngineEnum(engineString);
				} catch (BigDawgException ex) {
					ex.printStackTrace();
					throw new BigDawgCatalogException("Unsupported engine: "+ engineString);
				}
			}
		} catch (SQLException e) {
			cc.rollback();
			throw e;
		} finally {
			if (rs1 != null) rs1.close();
		}
		throw new BigDawgCatalogException("Cannot find engine name for dbid "+dbid);
	
	} 
	
	/**
	 * takes a integer DBID, returns a 5-field ArrayList<String> that tells host, port, dbname, userid and password
	 * 
	 * @param cc
	 * @param db_id
	 * @return
	 * @throws SQLException 
	 * @throws Exception
	 */
	public static ConnectionInfo getConnectionInfo(int dbid) throws BigDawgCatalogException, SQLException {
		
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		Engine e = getEngineOfDB(dbid);
		
		return IslandAndCastResolver.getQConnectionInfo(cc, e, dbid);
	}
	
	/**
	 * For each dbid, provide a CSV String of names of objects that reside on the database. 
	 * @param cc
	 * @param inputs
	 * @return HashMap<Integer, ArrayList<String>>
	 * @throws Exception
	 */
	public static HashMap<Integer, List<String>> getDBMappingByDB (List<String> inputs) throws BigDawgCatalogException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		CatalogUtilities.checkConnection(cc);
		if (inputs.size() == 0) throw new BigDawgCatalogException("Empty inputs from getDBMapping");
		
		int len = inputs.size();
		HashMap<Integer, List<String>> extraction = new HashMap<>();
		
		String wherePred = new String(" lower(o.name) = lower(\'"+ inputs.get(0) + "\') ");
		for (int i = 1; i < len; i++) {
			wherePred = wherePred + "or lower(o.name) = lower(\'" + inputs.get(i) + "\') ";
		}
		
		ResultSet rs = null;
		try {
			rs = cc.execRet("select physical_db db, string_agg(cast(name as varchar), ',') obj, count(name) c "
					+ "from (select * from catalog.objects o where " + wherePred + " order by name, physical_db) as objs "
					+ "group by physical_db order by c desc, physical_db;");
			
			if (rs.next()) extraction.put(rs.getInt("db"), new ArrayList<String>(Arrays.asList(rs.getString("obj").split(","))));
			while (rs.next()) {
				extraction.put(rs.getInt("db"), new ArrayList<String>(Arrays.asList(rs.getString("obj").split(","))));
			}
		} catch (SQLException e) {
			cc.rollback();
			throw e;
		} finally {
			if (rs != null) rs.close();
		}
		return extraction;
	};
	
	/**
	 * For each named object, provide a list of String of dbid of databases that holds its copy.
	 * @param inputs
	 * @return HashMap<String,ArrayList<String>>
	 * @throws UnsupportedIslandException 
	 * @throws SQLException 
	 */
	public static HashMap<String,List<String>> getDBMappingByObj (List<String> inputs, Scope scope) throws BigDawgCatalogException, UnsupportedIslandException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		
		CatalogUtilities.checkConnection(cc);
		if (inputs.size() == 0) throw new BigDawgCatalogException("Empty inputs from getDBMapping");
		
		int len = inputs.size();
		HashMap<String, List<String>> extraction = new HashMap<>();
		
		String wherePred = new String(" (lower(o.name) = lower(\'"+ inputs.get(0) + "\') ");
		for (int i = 1; i < len; i++) {
			wherePred = wherePred + " or lower(o.name) = lower(\'" + inputs.get(i) + "\') ";
		}
		wherePred += ")";
		
		String islandName = IslandAndCastResolver.getCatalogIslandSelectionPredicate(scope); 
		
		ResultSet rs = null;
		try {
			rs = cc.execRet("select o.name obj, string_agg(cast(physical_db as varchar), ',') db, count(o.name) c, scope_name island "
									+ "from catalog.objects o "
									+ "join catalog.databases d on o.physical_db = d.dbid "
									+ "join catalog.shims s on d.engine_id = s.engine_id "
									+ "join catalog.islands i on s.island_id = i.iid where " + wherePred 
									+ " AND scope_name = \'" + islandName + "\' "
									+ " group by o.name, island;");
			
			if (rs.next()) extraction.put(rs.getString("obj"), new ArrayList<String>(Arrays.asList(rs.getString("db").split(","))));
			while (rs.next()) {
				extraction.put(rs.getString("obj"), new ArrayList<String>(Arrays.asList(rs.getString("db").split(","))));
			}
		} catch (SQLException e) {
			cc.rollback();
			throw e;
		} finally {
			if (rs != null) rs.close();
		}
		
		if (extraction.isEmpty()) throw new BigDawgCatalogException("Cannot find inputs: "+inputs+"; in scope: "+scope.name()+"\n");
		
		return extraction;
	};
	
	/**
	 * With a CSV String of terms, fetch those that stands for an object. Used
	 * in within-island parser.
	 * 
	 * @param cc
	 * @param csvstr
	 * @return String of TSV String of object names (obj)
	 * @throws SQLException 
	 */
	public static String getObjectsFromList(String csvstr) throws BigDawgCatalogException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		
		// input check
		CatalogUtilities.checkConnection(cc);
		if (csvstr.length() == 0)
			return "";

		String[] strs = csvstr.split(",");
		int len = strs.length;
		String extraction = new String("");

		String wherePred = new String(" lower(o.name) = lower(\'" + strs[0].trim() + "\') ");
		for (int i = 1; i < len; i++) {
			wherePred = wherePred + "or lower(o.name) = lower(\'" + strs[i].trim() + "\') ";
		}

		ResultSet rs = null;
		try {
			rs = cc.execRet(
					"select distinct o.name obj " + "from catalog.objects o " + "where " + wherePred + "order by o.name;");
			if (rs.next())
				extraction = extraction + rs.getString("obj");
			while (rs.next()) {
				extraction = extraction + "\t" + rs.getString("obj");
			}
		} catch (SQLException e) {
			cc.rollback();
			throw e;
		} finally {
			if (rs != null) rs.close();
		}
		return extraction;
	}
	
	/**
	 * With name of procedure, fetch the data types of the parameters
	 * 
	 * @param procName
	 * @return The data types of the parameters
	 * @throws Exception
	 */
	public static ArrayList<String> getProcParamTypes(String procName) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		ArrayList<String> dataTypes = new ArrayList<String>();

		ResultSet rs = cc.execRet("select p.paramtypes from catalog.procedures p"
				+ " where p.name ilike \'" + procName + "%\';");

		while (rs.next()) {
			String dataTypesStr = rs.getString("paramtypes");
			dataTypes = new ArrayList<String>(Arrays.asList(dataTypesStr.replace(" ", "").split(",")));
		}
		rs.close();

		return dataTypes;
	}

	/**
	 * View all shims stored in catalog.
	 * 
	 * @param cc
	 * @return ArrayList of TSV String of shim_id, island name, engine name and
	 *         shim access_method
	 * @throws SQLException 
	 * @throws Exception
	 */
	@Deprecated
	public static List<String> getAllShims() throws BigDawgCatalogException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select shim_id, i.scope_name island, e.name engine, sh.access_method "
				+ "from catalog.shims sh " + "join catalog.islands i on sh.island_id = i.iid "
				+ "join catalog.engines e on e.eid = sh.engine_id;");
		while (rs.next()) {
			extraction.add(rs.getString("shim_id") + "\t" + rs.getString("island") + "\t" + rs.getString("engine")
					+ "\t" + rs.getString("access_method"));
		}

		return extraction;
	}
	
	
	private static List<String> getAllEngines() throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select * from catalog.engines;");
		while (rs.next()) {
			extraction.add(rs.getString("eid") + "\t" + rs.getString("name") + "\t" + rs.getString("host")
					+ "\t" + rs.getString("port") 
					+ "\t" + rs.getString("connection_properties"));
		}

		return extraction;
	}
	
	private static List<String> getAllDatabases() throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select * from catalog.databases;");
		while (rs.next()) {
			extraction.add(rs.getString("dbid") + "\t" + rs.getString("engine_id") + "\t" + rs.getString("name")
					+ "\t" + rs.getString("userid") + "\t" + rs.getString("password"));
		}

		return extraction;
	}
	

	/**
	 * View all casts stored in catalog.
	 * 
	 * @param cc
	 * @return ArrayList of TSV String of src engine name (src), dst engine name
	 *         (dst), and cast access_method
	 * @throws Exception
	 */
	private static List<String> getAllCasts() throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select e1.name src, e2.name dst, c.access_method " + "from catalog.casts c "
				+ "join catalog.engines e1 on c.src_eid = e1.eid " + "join catalog.engines e2 on c.dst_eid = e2.eid;");
		while (rs.next()) {
			extraction.add(rs.getString("src") + "\t" + rs.getString("dst") + "\t" + rs.getString("access_method"));
		}

		return extraction;
	}

	/**
	 * View all objects stored in catalog.
	 * 
	 * @param cc
	 * @return ArrayList of TSV String of object name (obj), fields, dbid of
	 *         physical_db, and name of engine (engine)
	 * @throws Exception
	 */
	@Deprecated
	public static List<String> getAllObjectsByEngine() throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select o.name obj, o.fields, d.name physical_db, e.name engine "
				+ "from catalog.objects o " + "left join catalog.databases d 	on o.physical_db = d.dbid "
				+ "join catalog.engines e 			on d.engine_id = e.eid;");
		while (rs.next()) {
			extraction.add(rs.getString("obj") + "\t" + rs.getString("fields") + "\t" + rs.getString("physical_db")
					+ "\t" + rs.getString("engine"));
		}

		return extraction;
	}
	
	@Deprecated
	public static List<String> getAllObjects(boolean includeFields) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select * from catalog.objects");
		while (rs.next()) {
			
			if (includeFields)
				extraction.add(rs.getString("oid") + "\t" + rs.getString("name") + "\t" + rs.getString("fields") 
					+ "\t" + rs.getString("logical_db") + "\t" + rs.getString("physical_db"));
			else 
				extraction.add(rs.getString("oid") + "\t" + rs.getString("name") + "\t" + rs.getString("logical_db")
					+ "\t" + rs.getString("physical_db"));
		}

		return extraction;
	}

	/**
	 * With an object's name, fetch all objects that share this name.
	 * 
	 * @param cc
	 * @param objName
	 * @return ArrayList of TSV String of object name (obj), fields,
	 *         physical_db, physical_db
	 * @throws Exception
	 */
	@Deprecated
	public static List<String> getObjectsByName(String objName) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);
		CatalogUtilities.checkLength(objName, 15);

		List<String> extraction = new ArrayList<String>();

		ResultSet rs = cc.execRet("select o.name obj, o.fields, d1.name physical_db, d2.name physical_db "
				+ "from catalog.objects o " + "join catalog.databases d1 on o.physical_db = d1.dbid "
				+ "join catalog.databases d2 on o.physical_db = d2.dbid " + "where o.name ilike  \'%" + objName
				+ "%\';");
		while (rs.next()) {
			extraction.add(rs.getString("obj") + "\t" + rs.getString("fields") + "\t" + rs.getString("physical_db")
					+ "\t" + rs.getString("physical_db"));
		}
		rs.close();

		return extraction;
	}
	


	/**
	 * With a list of objects, fetch all relevant one-step casts. Useful for
	 * migrating intermediate results; input one local table NOTE: if a cast is
	 * unavailable it will not show.
	 * 
	 * @param cc
	 * @param objs
	 * @return ArrayList of TSV String of dbName (db), source engine id
	 *         (src_id), destination engine id (src_id)
	 * @throws Exception
	 */
	public static List<String> getOneStepCastsUseObjects(List<String> objs) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);
		if (objs.size() == 0)
			return new ArrayList<String>();
		for (String objName : objs)
			CatalogUtilities.checkLength(objName, 15);

		List<String> extraction = new ArrayList<String>();
		List<String> objsdup = new ArrayList<String>();
		objsdup.addAll(objs.subList(1, objs.size()));
		String wherePred = new String(" o.name ilike \'%" + objs.get(0) + "%\' ");
		for (String objName : objsdup) {
			wherePred = wherePred + "or o.name ilike \'%" + objName + "%\' ";
		}

		ResultSet rs = cc.execRet("select distinct o.name obj, e1.name src, e2.name dst, c.access_method "
				+ "from catalog.objects o " + "join catalog.databases d 	on o.physical_db = d.dbid "
				+ "join catalog.casts c 		on c.src_eid = d.engine_id "
				+ "join catalog.engines e1		on c.src_eid = e1.eid "
				+ "join catalog.engines e2		on c.dst_eid = e2.eid " + "where " + wherePred
				+ " and c.src_eid != c.dst_eid " + "order by o.name, e1.name, e2.name;");
		while (rs.next()) {
			extraction.add(rs.getString("obj") + "\t" + rs.getString("src") + "\t" + rs.getString("dst") + "\t"
					+ rs.getString("access_method"));
		}
		rs.close();

		return extraction;
	}

	/**
	 * With a list of engines, fetch all relevant one-step casts.
	 * 
	 * @param cc
	 * @param src
	 * @param dst
	 * @return ArrayList of TSV String of source engine name (src), destination
	 *         engine name (dst) and cast access method (access_method)
	 * @throws Exception
	 */
	public static List<String> getOneStepCastsUseEngineNames(List<String> src_e, List<String> dst_e) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);
		if (src_e.size() == 0)
			return new ArrayList<String>();
		if (src_e.size() != dst_e.size())
			throw new Exception(
					"getOneStepCastsUseEngineNames - Lengths of source list and destination list do not match");
		for (String eName : src_e)
			CatalogUtilities.checkLength(eName, 15);
		for (String eName : dst_e)
			CatalogUtilities.checkLength(eName, 15);

		List<String> extraction = new ArrayList<String>();
		List<String> srcdup = new ArrayList<String>();
		srcdup.addAll(src_e.subList(1, src_e.size()));
		List<String> dstdup = new ArrayList<String>();
		dstdup.addAll(dst_e.subList(1, dst_e.size()));
		String wherePred = new String(
				"(e1.name ilike \'%" + src_e.get(0) + "%\' and e2.name ilike \'%" + dst_e.get(0) + "%\') ");
		for (String eName : srcdup) {
			wherePred = wherePred + "or (e1.name ilike \'%" + eName + "%\' and e2.name ilike \'%"
					+ dstdup.get(srcdup.indexOf(eName)) + "%\') ";
		}

		ResultSet rs = cc.execRet("select distinct e1.name src, e2.name dst, c.access_method "
				+ "from catalog.engines e1 " + "join catalog.casts c 	on e1.eid = c.src_eid "
				+ "join catalog.engines e2 	on e2.eid = c.dst_eid " + "where " + wherePred
				+ "order by e1.name, e2.name;");
		while (rs.next()) {
			extraction.add(rs.getString("src") + "\t" + rs.getString("dst") + "\t" + rs.getString("access_method"));
		}
		rs.close();

		return extraction;
	}

	/**
	 * With a list of source and destination db ids, fetch shim access method
	 * 
	 * @param cc
	 * @param src_db
	 * @param dst_db
	 * @return ArrayList of TSV String of source database id (src_db),
	 *         destination database id (dst_db), source engine (src_ename),
	 *         destination engine (dst_ename) and cast access method
	 *         (access_method)
	 * @throws Exception
	 */
	public static List<String> getOneStepCastsUseDbToDb(List<String> src_db, List<String> dst_db) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);
		if (src_db.size() == 0)
			return new ArrayList<String>();
		if (src_db.size() != dst_db.size())
			throw new Exception("getOneStepCastsUseDbToDb - Lengths of source list and destination list do not match");
		for (String dbName : src_db)
			CatalogUtilities.checkLength(dbName, 15);
		for (String dbName : dst_db)
			CatalogUtilities.checkLength(dbName, 15);

		List<String> extraction = new ArrayList<String>();
		List<String> srcdup	 = new ArrayList<String>();
		srcdup.addAll(src_db.subList(1, src_db.size()));
		List<String> dstdup 	= new ArrayList<String>();
		dstdup.addAll(dst_db.subList(1, dst_db.size()));
		String wherePred = new String(
				"(d1.name ilike \'%" + src_db.get(0) + "%\' and d2.name ilike \'%" + dst_db.get(0) + "%\') ");
		for (String dbName : srcdup) {
			wherePred = wherePred + "or (d1.name ilike \'%" + dbName + "%\' and d2.name ilike \'%"
					+ dstdup.get(srcdup.indexOf(dbName)) + "%\') ";
		}

		ResultSet rs = cc.execRet(
				"select distinct d1.name src_db, d2.name dst_db, e1.name src_engine, e2.name dst_engine, c.access_method "
						+ "from catalog.databases d1 " + "join catalog.engines e1 		on d1.engine_id = e1.eid "
						+ "join catalog.casts c 		on e1.eid = c.src_eid "
						+ "join catalog.engines e2 		on e2.eid = c.dst_eid "
						+ "join catalog.databases d2 	on e2.eid = d2.engine_id " + "where " + wherePred
						+ "order by d1.name, d2.name, e1.name, e2.name;");
		while (rs.next()) {
			extraction.add(rs.getString("src_db") + "\t" + rs.getString("dst_db") + "\t" + rs.getString("src_engine")
					+ "\t" + rs.getString("dst_engine") + "\t" + rs.getString("access_method"));
		}
		rs.close();

		return extraction;
	}

	/**
	 * With a list of objects and corresponding list of destination islands,
	 * find out where could we route data in one step
	 * 
	 * @param cc
	 * @param objs
	 * @param islands
	 * @return ArrayList of TSV String of object name (obj), source and
	 *         destination dbid (src_db, dst_db), island name (island) and cast
	 *         access_method
	 * @throws Exception
	 */
	public static List<String> getOneStepCastDbsUseObjectsIslands(List<String> objs, List<String> islands) throws Exception {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);
		if (objs.size() == 0)
			return new ArrayList<String>();
		if (objs.size() != islands.size())
			throw new Exception(
					"getObjectsOneStepCastDbsUseIsland - Lengths of objs list and islands list do not match");
		for (String objName : objs)
			CatalogUtilities.checkLength(objName, 15);
		for (String iName : islands)
			CatalogUtilities.checkLength(iName, 15);

		List<String> extraction = new ArrayList<String>();
		List<String> objdup = new ArrayList<String>();
		objdup.addAll(objs.subList(1, objs.size()));
		List<String> isldup = new ArrayList<String>();
		isldup.addAll(islands.subList(1, islands.size()));
		String wherePred = new String(
				"(o.name ilike \'%" + objs.get(0).replaceAll("%", "\\%") + "%\' and i.scope_name ilike \'%" + islands.get(0) + "%\') ");
		for (String objName : objdup) {
			wherePred = wherePred + "or (o.name ilike \'%" + objName + "%\' and i.scope_name ilike \'%"
					+ isldup.get(objdup.indexOf(objName)) + "%\') ";
		}

		ResultSet rs = cc
				.execRet("select o.name obj, d1.name src_db, d2.name dst_db, i.scope_name island, c.access_method "
						+ "from catalog.objects o " + "join catalog.databases d1 	on o.physical_db = d1.dbid "
						+ "join catalog.engines e1 		on d1.engine_id = e1.eid "
						+ "join catalog.casts c 		on e1.eid = c.src_eid "
						+ "join catalog.engines e2 		on c.dst_eid = e2.eid "
						+ "join catalog.databases d2 	on d2.engine_id = e2.eid "
						+ "join catalog.shims sh 		on e2.eid = sh.engine_id "
						+ "join catalog.islands i 		on sh.island_id = i.iid " + "where " + wherePred
						+ "order by o.name, d1.name, d2.name, i.scope_name;");
		while (rs.next()) {
			extraction.add(rs.getString("obj") + "\t" + rs.getString("src_db") + "\t" + rs.getString("dst_db") + "\t"
					+ rs.getString("island") + "\t" + rs.getString("access_method"));
		}
		rs.close();

		return extraction;
	}

	/**
	 * Returns the object names that have the specified engine and database
	 * @param engine
	 * @param database
	 * @return
	 * @throws BigDawgCatalogException
	 * @throws SQLException
	 */
	public static List<String> getObjectNames(String engine, String database) throws BigDawgCatalogException, SQLException {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		// input check
		CatalogUtilities.checkConnection(cc);

		List<String> result = new ArrayList<String>();

		PreparedStatement stmt = cc.connection.prepareStatement("select o.name obj "
						+ "from catalog.objects o " + "join catalog.databases d1 on o.physical_db = d1.dbid "
						+ "join catalog.engines e1 		on d1.engine_id = e1.eid "
						+ "where e1.name = ? and d1.name = ?");

		stmt.setString(1, engine);
		stmt.setString(2, database);
		stmt.execute();
		ResultSet rs = stmt.getResultSet();
		while (rs.next()) {
			result.add(rs.getString("obj"));
		}
		return result;
	}

	/**
	 * Used for updating catalog entries.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Catalog cc = CatalogInstance.INSTANCE.getCatalog();
		try {
			CatalogUtilities.checkConnection(cc);
			
			List<String> result;
			
			result = getAllEngines();
			System.out.println("\neid\tname\thost\tconnection_properties");	
			for (String s : result) {
				System.out.println(s);
			}
			
			result = getAllDatabases();
			System.out.println("\ndbid\tengine_id\tname\tuserid\tpassword");	
			for (String s : result) {
				System.out.println(s);	
			}
			
			result = getAllObjects(false);
			System.out.println("\noid\tname\tlogical_db\tphysical_db");	
			for (String s : result) {
				System.out.println(s);
			}
			
			result = getAllCasts();
			System.out.println("\nCasts:\noid\tname\tlogical_db\tphysical_db");	
			for (String s : result) {
				System.out.println(s);
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}