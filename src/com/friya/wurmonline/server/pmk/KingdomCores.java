package com.friya.wurmonline.server.pmk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gotti.wurmunlimited.modsupport.ModSupportDb;
import com.mb3364.http.AsyncHttpClient;
import com.mb3364.http.HttpClient;
import com.mb3364.http.RequestParams;
import com.mb3364.http.StringHttpResponseHandler;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.support.JSONObject;


public class KingdomCores
{
	private static Logger logger = Logger.getLogger(KingdomCores.class.getName());
	private static KingdomCores instance;
	private static HashMap<Long, KingdomCore> coreItems = new HashMap<Long, KingdomCore>();

	private long lastPoll = 0;
	private long pollCounter = 0;
	private int pollFrequency = 1000 * (Mod.isTestEnv() ? 10 : 30);		// * seconds
	private long jsonVersion = 1;										// Bump if the data structure we pass to website changes.


	static KingdomCores getInstance()
	{
		if(instance == null) {
			instance = new KingdomCores();
		}

		return instance; 
	}


	KingdomCores()
	{
		initialize();
		loadAll();
	}


	void pollCores()
	{
		if((lastPoll + pollFrequency) > System.currentTimeMillis()) {
			return;
		}

		logger.finest("Polling tracked cores...");

		lastPoll = System.currentTimeMillis();

		Item i;
		List<KingdomCore> movedCores = new ArrayList<KingdomCore>();
		List<KingdomCore> removedCores = new ArrayList<KingdomCore>();
		Collection<KingdomCore> cores = coreItems.values();

		for(KingdomCore kc : cores) {
			i = kc.getItem();

			if(i == null) {
				logger.fine("Kingdom Core item is no longer found: " + kc.getItemId() + " removing it from tracker");
				kc.delete();
				
				// Make sure we notify external website when we are destroyed as well.
				removedCores.add(kc);
				movedCores.add(kc);
				continue;
			}

			// Pass on one mandatory update to remote server at least once per reboot.
			if(kc.poll(i) || pollCounter == 2) {
				movedCores.add(kc);
			}

			// Apparently the effect only show up for people currently logged in, so force an update of the beam every N polls.
			if(pollCounter % 5 == 1) {
				logger.finest("Make Kingdom Core beams show, PLEASE... " + pollCounter);
				kc.updateBeam();
			}

			pollCounter++;
		}

		if(movedCores.size() > 0) {
			updateWebsite(movedCores);
		}

		// This must be after we send update to external website as we want to give notice of destruction of cores.
		if(removedCores.size() > 0) {
			for(KingdomCore kc : removedCores) {
				removeTrackedCore(kc);
			}
		}
	}


	private void removeTrackedCore(KingdomCore core)
	{
		coreItems.remove(core.getItemId());
	}
	

	void addTrackedCore(KingdomCore core)
	{
		core.save();
		coreItems.put(core.getItemId(), core);
	}
	

	private void loadAll()
	{
		logger.log(Level.INFO, "Loading all Kingdom Cores...");

		Connection dbcon = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		try {
			dbcon = ModSupportDb.getModSupportDb();
		    ps = dbcon.prepareStatement("SELECT * FROM FriyaKingdomCores");
		    rs = ps.executeQuery();

			while (rs.next()) {
				logger.log(Level.INFO, "Creating Kingdom Core tracker for: " + rs.getLong("itemid"));
		        coreItems.put(rs.getLong("itemid"), new KingdomCore(
						rs.getLong("itemid"),
						rs.getInt("hometilex"),
						rs.getInt("hometiley"),
						rs.getString("homekingdom"),
						rs.getString("spawner"),
						rs.getLong("created"),
						rs.getLong("seenathome"),
						true						// updateStatus (should be true when loading, but not when creating)
				));
		    }
			rs.close();
			ps.close();
		}
		catch (SQLException e) {
		    throw new RuntimeException(e);
		}
	}


	private void initialize()
	{
		try {
			Connection con = ModSupportDb.getModSupportDb();
			String sql = "";

			if(ModSupportDb.hasTable(con, "FriyaKingdomCores") == false) {
				logger.log(Level.INFO, "Initializing Kingdom Cores for the first time, creating table FriyaKingdomCores...");

				sql = "CREATE TABLE FriyaKingdomCores ("
					+ "		itemid					BIGINT			NOT NULL PRIMARY KEY,"
					+ "		hometilex				INT				NOT NULL DEFAULT 0,"
					+ "		hometiley				INT				NOT NULL DEFAULT 0,"
					+ "		homekingdom				VARCHAR(99)		NOT NULL DEFAULT 'Unknown',"
					+ "		spawner					VARCHAR(40)		NOT NULL DEFAULT 'Unknown',"
					+ "		seenathome				BIGINT			NOT NULL DEFAULT 0,"
					+ "		created					BIGINT			NOT NULL DEFAULT 0"
					+ ")";
				PreparedStatement ps = con.prepareStatement(sql);
				ps.execute();
				ps.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}


	// https://github.com/urgrue/java-async-http
	// https://www.tutorialspoint.com/sqlite/sqlite_php.htm
	private boolean updateWebsite(List<KingdomCore> cores)
	{
		String url = Mod.updateStatusUrl;

		// JSONObject.put() will iterate over all objects in the collection and get all PUBLIC getters in the object (dodgy shit that)
		JSONObject j = new JSONObject().put("cores", cores);
		j.put("dataVersion", jsonVersion);

		String json = j.toString();

		// Log everything we send over so we have an audit trail locally (keep at INFO)
		logger.info("Kingdom Core update: " + json);

    	// Prepare the post
		RequestParams params = new RequestParams();
		params.put("secret", Mod.updateStatusSecret);
		params.put("data", json);

		HttpClient client = new AsyncHttpClient();
		client.setUserAgent("Friya-Kingdom-Cores");

		client.post(url, params, new StringHttpResponseHandler() {
		    @Override
		    public void onSuccess(int statusCode, Map<String, List<String>> headers, String content)
		    {
		    	logger.info("Got response from status update: " + content);
		    }

		    @Override
		    public void onFailure(int statusCode, Map<String, List<String>> headers, String content)
		    {
		    	// 403, 404 etc
		    	logger.severe("Failed to update external website with Kingdom Core status. Response code was: " + statusCode);
		    }

		    @Override
		    public void onFailure(Throwable throwable)
		    {
		        // An exception occurred during the request. Usually unable to connect or there was an error reading the response
		    	logger.log(Level.SEVERE, "Failed to update external website with Kingdom Core status. See this error for more details: ", throwable);
		    }
		});

		return true;
	}
}
