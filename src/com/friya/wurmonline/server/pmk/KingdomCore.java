package com.friya.wurmonline.server.pmk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gotti.wurmunlimited.modsupport.ModSupportDb;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.kingdom.Kingdom;
import com.wurmonline.server.kingdom.Kingdoms;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.shared.constants.EffectConstants;


public class KingdomCore
{
	private static Logger logger = Logger.getLogger(KingdomCore.class.getName());

	// These variables are persisted.
	private long itemId = -1;							// The Wurm ID of the actual in-game item
	private int homeTileX = -1;							// Tile at which it was first spawned
	private int homeTileY = -1;							// Tile at which it was first spawned
	private String homeKingdom = "None";				// Which kingdom it was first spawned in
	private String spawner = "Unknown";					// Which GM first created it
	private long created = 0;							// When GM first created it
	private long seenAtHome = 0;						// Convenience timestamp for when core was last at home

	// These variables are NOT persisted, but updated when the item is polled.
	private long lastPoll = 0;							// When we last polled for the existance of this core
	private int lastTileX = -1;							// Where it was found at last poll
	private int lastTileY = -1;							// Where it was found at last poll
	private long lastContainer = -1;					// Wurm ID of wagon, etc.
	private boolean lastSurface = true;					// On surface or not
	private String lastMovedBy = null;					// This is last known driver of any vehicle

	// Internal status flags
	private long beamEffectId = -1;						// This is to track the effect (red beam) going out of the core.
	private boolean destroyed = false;					// Flag to say whether this item is destroyed so we can send that info to remote website


	KingdomCore(long coreItemId, int homeX, int homeY, String kingdomName, String spawnerName, long createdTimestamp, long whenSeenAtHome, boolean updateStatus)
	{
		itemId = coreItemId;
		homeTileX = homeX;
		homeTileY = homeY;
		homeKingdom = kingdomName;
		spawner = spawnerName;
		created = createdTimestamp;
		seenAtHome = whenSeenAtHome;
		
		if(updateStatus) {
			updateStatus();
		}
	}


	/**
	 * poll()
	 * 
	 * @param item Kingdom Core item
	 * @return true if core was moved, false if not
	 */
	boolean poll(Item item)
	{
		if(isMoved(item)) {
			updateStatus();
			return true;
		} else {
			updateLastPolled();
			return false;
		}
	}

	private void updateStatus()
	{
		if(destroyed) {
			logger.warning("KingdomCore is flagged for destruction, will not update status.");
			return;
		}
		
		Item i = getItem();
		
		if(i == null) {
			// Let something else deal with this problem.
			logger.warning("Attempting to update current location for a Kingdom Core that does not exist.");
			return;
		}
		
		updateLastPolled();
		
		lastTileX = i.getTileX();
		lastTileY = i.getTileY();
		lastContainer = i.getParentId();
		lastSurface = i.isOnSurface();

		if(lastContainer != -10) {
			try {
				Item container = Items.getItem(lastContainer);
				if(container != null) {
					Vehicle v = Vehicles.getVehicle(container);
					long pilotId = v.getPilotId();
					if(pilotId >= 0) {
						lastMovedBy = Players.getInstance().getNameFor(pilotId);
					}
				}
			} catch (NoSuchPlayerException | IOException | NoSuchItemException e) {
				// We can quietly ignore this as we don't expect to have a driver or container at all times, we only want who we had last.
			}
		}

		updateBeam();
	}

	
	private void updateLastPolled()
	{
		if(isHome()) {
			seenAtHome = System.currentTimeMillis();
		}

		lastPoll = System.currentTimeMillis();
	}
	

	Item getItem()
	{
		if(itemId <= 0) {
			return null;
		}

		try {
			return Items.getItem(itemId);
		} catch (NoSuchItemException e) {
			logger.log(Level.WARNING, "Kingdom core no longer existed, it was probably detroyed");
		}

		return null;
	}
	

	private String getCurrentKingdomName()
	{
		byte kingdomId = Zones.getKingdom(lastTileX, lastTileY);
		Kingdom k = Kingdoms.getKingdomOrNull(kingdomId);

		if(k == null) {
			return "n/a";
		}
		
		return k.getName();
	}
	

	private String getCurrentDeed()
	{
		Village v = Zones.getVillage(lastTileX, lastTileY, true);

		if(v == null) {
			return "n/a";
		}
		
		return v.getName();
	}
	
	
	private boolean isOnToken()
	{
		Village v = Zones.getVillage(lastTileX, lastTileY, true);

		if(v == null) {
			return false;
		}
		
		if(lastTileX != v.getTokenX() || lastTileY != v.getTokenY() && lastSurface) {
			return false;
		}

		return true;
	}
	

	private boolean isOnHostileToken()
	{
		if(isOnToken() && getCurrentKingdomName().equals(homeKingdom) == false) {
			return true;
		}

		return false;
	}
	

	void save()
	{
		if(destroyed) {
			logger.warning("Kingdom Core tracker is destroyed, refusing to save");
			return;
		}
		
		try {
			String sql = null;
			boolean update = existsInDb();
			
			if(update) {
				sql = ("UPDATE FriyaKingdomCores "
						+ "SET itemid = ?, hometilex = ?, hometiley = ?, homekingdom = ?, spawner = ?, seenathome = ?, created  = ? "
						+ "WHERE itemid = ?"
				);
				
			} else {
				sql = "INSERT INTO FriyaKingdomCores"
						+ " ("
						+ "		itemid, hometilex, hometiley, homekingdom, spawner, seenathome, created"
						+ " )"
						+ " VALUES(?,?,?,?,?,?,?)";
			}
			
			Connection dbcon = ModSupportDb.getModSupportDb();
			PreparedStatement ps = dbcon.prepareStatement(sql);

			int i = 1;
			ps.setLong(i++, itemId);
			ps.setInt(i++, homeTileX);
			ps.setInt(i++, homeTileY);
			ps.setString(i++, homeKingdom);
			ps.setString(i++, spawner);
			ps.setLong(i++, seenAtHome);
			ps.setLong(i++, System.currentTimeMillis());
			
			if(update) {
				ps.setLong(i++, itemId);
			}

			ps.execute();
			ps.close();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to update/insert kingdom core tracker");
			throw new RuntimeException(e);
		}
	}


	void delete()
	{
		if(destroyed) {
			return;
		}

		if(beamEffectId > 0) {
			destroyBeam();
		}

		if(itemId == -1 || !existsInDb()) {
			return;
		}
		
		logger.info("Deleting Kingdom Core tracker: " + itemId);
		
		try {
			Connection dbcon = ModSupportDb.getModSupportDb();
			PreparedStatement ps = dbcon.prepareStatement("DELETE FROM FriyaKingdomCores WHERE itemid = ?");
			ps.setLong(1, itemId);
			ps.execute();
			ps.close();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to delete kingdom core");
			throw new RuntimeException(e);
		}

		Item i = getItem();
		if(i != null) {
			Items.destroyItem(i.getWurmId());
		}
		
		// Flag it as destroyed, it will still linger for a little while to pass update to external website
		destroyed = true;
	}


	private boolean existsInDb()
	{
		if(itemId == -1) {
			return false;
		}
		
		Connection dbcon = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		int foundCount = 0;
		
		try {
			dbcon = ModSupportDb.getModSupportDb();
		    ps = dbcon.prepareStatement("SELECT COUNT(*) AS cnt FROM FriyaKingdomCores WHERE itemid = " + itemId);
		    rs = ps.executeQuery();

			if (rs.next()) {
				foundCount = rs.getInt("cnt");
		    }
			rs.close();
			ps.close();
		}
		catch (SQLException e) {
		    throw new RuntimeException(e);
		}
		
		return foundCount > 0;
	}


	private boolean isMoved(Item item)
	{
		if(item.getTileX() != lastTileX || item.getTileY() != lastTileY || item.isOnSurface() != lastSurface || item.getParentId() != lastContainer) {
			return true;
		}

		return false;
	}
	

	/**
	 * As opposed to isHome(Item), this will check against our last stored poll-data instead of the item's
	 * actual position.
	 * 
	 * @return true if it was at home last time it was polled
	 */
	private boolean isHome()
	{
		if(lastTileX != homeTileX || lastTileY != homeTileY || lastSurface == false || lastContainer != -10) {
			return false;
		}

		return true;
	}
	

	/**
	 * Is the actual *item* at its hometile?
	 * 
	 * @param item the core-item
	 * @return true if it is at home
	 */
	private boolean isHome(Item item)
	{
		if(item == null) {
			return false;
		}
		
		if(item.getTileX() != homeTileX || item.getTileY() != homeTileY || item.isOnSurface() == false || item.getParentId() != -10) {
			return false;
		}

		return true;
	}
	

	private void createBeam()
	{
		if(beamEffectId >= 0) {
			destroyBeam();
		}
		
		if(destroyed) {
			return;
		}

		beamEffectId = Server.rand.nextInt(12345678) + 123456789;
		Players.getInstance().sendGlobalNonPersistantEffect(beamEffectId, EffectConstants.RIFT_SPAWN, lastTileX, lastTileY, 
			Tiles.decodeHeightAsFloat(
				(int)Server.surfaceMesh.getTile(lastTileX, lastTileY)
			)
		);
	}
	

	private void destroyBeam()
	{
		Players.getInstance().removeGlobalEffect(beamEffectId);
		beamEffectId = -1;
	}
	

	void updateBeam()
	{
		// Create will also destroy beams if they exist.
		createBeam();
	}
	

	private ArrayList<String> getLocalNamesArray()
	{
        Player[] ps = Players.getInstance().getPlayers();
        ArrayList<String> nearByPlayers = new ArrayList<String>();

        for (Player p : ps) { 
            if (p == null || !p.isWithinDistanceTo(getItem(), 300.0f)) {
            	continue;
            }

           	nearByPlayers.add(p.getName());
        }
        
        return nearByPlayers;
	}


	//
	// Public getters that are handed to external tracking website.
	//

	public long getItemId()				{ return itemId;	}
	public String getName()				{ return getItem().getName().replace("\"", ""); }
	public String getHomeKingdom()		{ return homeKingdom.replace("\"", ""); }
	public String getHomeCoordinate()	{ return homeTileX + " x " + homeTileY; }
	public String getCoordinate()		{ return lastTileX + " x " + lastTileY;	}
	public int getDistanceFromHome()	{ return Math.max(Math.abs(lastTileX - homeTileX), Math.abs(lastTileY - homeTileY)); }
	public String getKingdom()			{ return getCurrentKingdomName().replace("\"", ""); }
	public String getVillage()			{ return getCurrentDeed().replace("\"", ""); }
	public boolean getSurfaceStatus()	{ return lastSurface; }
	public boolean getHomeStatus()		{ return isHome(getItem()); }
	public String getLastMovedBy()		{ return lastMovedBy == null ? "" : lastMovedBy; }
	public boolean getIsOnToken()		{ return isOnToken() && lastContainer == -10; }
	public boolean getIsOnHostileToken(){ return isOnHostileToken() && lastContainer == -10; }
	public String getSpawner()			{ return spawner; }
	public long getCreationTime()		{ return created / 1000; }
	public long getLastPolled()			{ return lastPoll / 1000; }
	public boolean getIsDestroyed()		{ return destroyed; }
	public long getLastSeenAtHome()		{ return seenAtHome / 1000; }
	public String getServerName()		{ return Servers.localServer.getName().replace("\"", ""); }
	public String getLocalNames()		{ return getLocalNamesArray().toString(); }
	
	public String getVehicleOwner()
	{
		if(lastContainer != -10) {
			try {
				Item container = Items.getItem(lastContainer);
				if(container != null) {
					return container.getOwnerName();
				}
			} catch (NoSuchItemException e) {
			}
		}
		
		return "";
	}
	
	public String getVehicle()
	{
		if(lastContainer != -10) {
			try {
				Item container = Items.getItem(lastContainer);
				if(container != null) {
					DecimalFormat df = new DecimalFormat("#.##");
					return container.getTemplate().getName() + " (" + df.format(container.getCurrentQualityLevel()) + "ql), " + container.getName().replace("\"", "");
				}
			} catch (NoSuchItemException e) {
			}
		}
		
		return "";
	}
}
