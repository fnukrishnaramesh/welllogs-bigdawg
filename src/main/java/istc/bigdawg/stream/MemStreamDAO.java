package istc.bigdawg.stream;

import istc.bigdawg.exceptions.AlertException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author aelmore A dead simple in memory class for storing stream data
 *         objects.
 */
public class MemStreamDAO extends StreamDAO {
	private List<ClientAlert> clientAlerts;
	private List<DBAlert> dbAlerts;
	private List<AlertEvent> events;
	private AtomicInteger clientCounter = new AtomicInteger();
	private AtomicInteger dbCounter = new AtomicInteger();
	private AtomicInteger eventCounter = new AtomicInteger();

	public static final MemStreamDAO INSTANCE = new MemStreamDAO(); 
	
	public static StreamDAO getStreamDAO() {
		return INSTANCE;
	}
	
	private MemStreamDAO() {
		clientAlerts = new Vector<ClientAlert>();
		dbAlerts = new Vector<DBAlert>();
		events = new Vector<AlertEvent>();
		clientCounter = new AtomicInteger();
		dbCounter = new AtomicInteger();
		eventCounter = new AtomicInteger();
	}

	public void reset() {
		clientAlerts = new Vector<ClientAlert>();
		dbAlerts = new Vector<DBAlert>();
		events = new Vector<AlertEvent>();
		clientCounter = new AtomicInteger();
		dbCounter = new AtomicInteger();
		eventCounter = new AtomicInteger();
	}

	@Override
	public DBAlert createOrGetDBAlert(String storedProc, boolean oneTime) {
		for (DBAlert a : this.dbAlerts) {
			if (a.stroredProc.equalsIgnoreCase(storedProc)) {
				return a;
			}
		}
		DBAlert a = new DBAlert(dbCounter.getAndIncrement(), storedProc,
				oneTime, "null");
		dbAlerts.add(a);
		return a;
	}

	@Override
	public ClientAlert createClientAlert(int dbAlertId, boolean push,
			boolean oneTime, String pushURL) throws AlertException {
		DBAlert dbAlert = null;
		for (DBAlert a : this.dbAlerts) {
			if (a.dbAlertID == dbAlertId) {
				dbAlert = a;
			}
		}
		if (dbAlert == null) {
			throw new AlertException("Missing DB alert " + dbAlertId);
		}
		for (ClientAlert a : this.clientAlerts) {
			if ((a.pushURL != null && a.pushURL.equalsIgnoreCase(pushURL))
					&& a.active) {
				a.push = push;
				a.oneTime = oneTime;
				return a;
			}
		}
		ClientAlert alert = new ClientAlert(clientCounter.getAndIncrement(),
				dbAlertId, push, oneTime, true, false, pushURL);
		clientAlerts.add(alert);
		return alert;
	}

	@Override
	public List<AlertEvent> addAlertEvent(int dbAlertId, String body) {
		AlertEvent event = new AlertEvent(eventCounter.getAndIncrement(),
				dbAlertId, body);
		events.add(event);
		List<AlertEvent> clientsToNotify = new ArrayList<AlertEvent>();
		for (ClientAlert a : this.clientAlerts) {
			if (a.dbAlertID == dbAlertId && a.active) {
				// found a match
				clientsToNotify.add(new AlertEvent(a.clientAlertID, dbAlertId, body));

			}
		}
		return clientsToNotify;
	}

	@Override
	public List<PushNotify> updatePullsAndGetPushURLS(List<AlertEvent> alerts) {
		List<PushNotify> urls = new ArrayList<PushNotify>();

		for (AlertEvent event : alerts) {
			for (ClientAlert a: this.clientAlerts)
				if (a.clientAlertID == event.alertID) {
					// found a match
					if (a.push) {
						urls.add(new PushNotify(a.pushURL, event.body));
						if (a.oneTime) {
							a.active = false;
						}
					} else {
						// pull
						a.unseenPulls.add(event.body);
						a.lastPullTime = new Date();
	
					}
	
				}
		}
		return urls;
	}

	@Override
	public String checkForNewPull(int clientAlertId) {
		for (ClientAlert a : this.clientAlerts) {
			if (a.clientAlertID == clientAlertId && a.active && !a.push
					&& !a.unseenPulls.isEmpty()) {
				// disable if onetime
				if (a.oneTime)
					a.active = false;
				// We have seen it
				String ret = null;
				JSONArray data = null;
				if (a.unseenPulls.size()==1)
					try {
						data = (JSONArray)new JSONParser().parse(a.unseenPulls.get(0));
						
					} catch (ParseException e2) {
						e2.printStackTrace();
					}
				else if (a.unseenPulls.size()>1){
					
					for (String e : a.unseenPulls){
						try{
							if (data == null){
								data = (JSONArray)new JSONParser().parse(e);
							} else {
								data.addAll((JSONArray)new JSONParser().parse(e));
							}
						} catch (ParseException e1) {
							e1.printStackTrace();
							return e1.toString();
						}
					}
				}
				JSONObject obj = new JSONObject();
				obj.put("data", data);
				ret =obj.toString();
				a.unseenPulls.clear();
				return ret;
			}
		}
		return "None";
	}

	@Override
	public List<DBAlert> getDBAlerts() {
		return dbAlerts;
	}

	@Override
	public List<ClientAlert> getActiveClientAlerts() {
		List<ClientAlert> cas = new ArrayList<ClientAlert>();
		for (ClientAlert a : this.clientAlerts) {
			if (a.active) {
				cas.add(a);
			}
		}
		return cas;
	}

}
