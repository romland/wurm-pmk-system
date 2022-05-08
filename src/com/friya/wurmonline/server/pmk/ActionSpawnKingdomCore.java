package com.friya.wurmonline.server.pmk;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.Materials;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.kingdom.Kingdom;
import com.wurmonline.server.kingdom.Kingdoms;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zones;


public class ActionSpawnKingdomCore implements ModAction
{
	private static short actionId;
	private static Logger logger = Logger.getLogger(ActionSpawnKingdomCore.class.getName());
	private final ActionEntry actionEntry;
	

	static public short getActionId()
	{
		return actionId;
	}
	

	public ActionSpawnKingdomCore()
	{
		logger.log(Level.INFO, "ActionSpawnKingdomCore()");

		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(
			actionId, 
			"Spawn Kingdom Core", 
			"spawning Kingdom Core",
			new int[] { 6 }			// 6 ACTION_TYPE_NOMOVE
		);
		ModActions.registerAction(actionEntry);
	}


	private boolean isAllowed(Creature performer)
	{
		if (performer.getPower() < 1 || performer.isOnSurface() == false) {
			return false;
		}
		
		return true;
	}


	@Override
	public BehaviourProvider getBehaviourProvider()
	{
		return new BehaviourProvider() {

			@Override
			public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, int tilex, int tiley, boolean onSurface, int tile)
			{
				return getBehavioursFor(performer, tilex, tiley, onSurface, tile);
			}

			@Override
			public List<ActionEntry> getBehavioursFor(Creature performer, int tilex, int tiley, boolean onSurface, int tile, int dir)
			{
				return getBehavioursFor(performer, tilex, tiley, onSurface, tile);
			}

			@Override
			public List<ActionEntry> getBehavioursFor(Creature performer, int tilex, int tiley, boolean onSurface, int tile)
			{
				if(!isAllowed(performer)) {
					return null;
				}

				return Arrays.asList(actionEntry);
			}
		};
	}

	
	@Override
	public ActionPerformer getActionPerformer()
	{
		return new ActionPerformer() {

			@Override
			public short getActionId() {
				return actionId;
			}
			
			
			public boolean action(Action act, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short action, float counter)
			{
				action(act, performer, tilex, tiley, onSurface, tile, action, counter);
				return true;
			}


			public boolean action(Action act, Creature performer, int tilex, int tiley, boolean onSurface, int tile, short action, float counter)
			{
				if(!isAllowed(performer)) {
					return true;
				}
				
				logger.log(Level.INFO, performer.getName() + " is spawning a kingdom core at " + tilex + ", " + tiley + "...");
				performer.getCommunicator().sendNormalServerMessage("Spawning a Kingdom Core here...");

				byte kingdomId = Zones.getKingdom(tilex, tiley);
				Kingdom k = Kingdoms.getKingdomOrNull(kingdomId);

				if(k == null) {
					performer.getCommunicator().sendNormalServerMessage("There is no kingdom here, refusing to spawn kingdom core!");
					return true;
				}

				Village v = Zones.getVillage(tilex, tiley, onSurface);
				if(v == null) {
					performer.getCommunicator().sendNormalServerMessage("There is no village here, refusing to spawn kingdom core!");
					return true;
				}
				
				if(tilex != v.getTokenX() || tiley != v.getTokenY()) {
					performer.getCommunicator().sendNormalServerMessage("Don't be a Jaygriff. You can only spawn kingdom cores on tiles that contain a deed token!");
					return true;
				}
				
				Item item = null;
				try {
					item = ItemFactory.createItem(
                		SetupItemKingdomCore.getId(),
                        99.99f,
                        (float)(tilex << 2) + 2.0f, 
                        (float)(tiley << 2) + 2.0f, 
                        Server.rand.nextFloat() * 360.0f,
                        onSurface, 
                        (byte)3,						// rarity
                        -10, 							// bridge
                        null							// creator
                    );  
					item.setMaterial(Materials.MATERIAL_GOLD);
					
					item.setName(item.getName() + " - " + k.getName());
					item.updateName();
					item.sendUpdate();
					
					KingdomCore kc = new KingdomCore(
							item.getWurmId(), 
							tilex, 
							tiley, 
							k.getName(), 
							performer.getName(), 
							System.currentTimeMillis(),
							System.currentTimeMillis(),
							false						// updateStatus = false, will trigger a notification to external server at first chance
					);
					KingdomCores.getInstance().addTrackedCore(kc);

				} catch (FailedException | NoSuchTemplateException e) {
					performer.getCommunicator().sendNormalServerMessage("Could not spawn Kingdom Core item, check error log on server...");
					logger.log(Level.SEVERE, "Could not spawn Kingdom Core item", e);
				}
				
				return true;
			}

		}; // ActionPerformer
	}
}
