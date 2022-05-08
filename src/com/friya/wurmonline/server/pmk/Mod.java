/*
Zenath's Player Made Kingdoms (PMK) -- http://zenath.net/forums/showthread.php?tid=158
https://forum.wurmonline.com/index.php?/topic/144753-zenath-the-beginning-pvp-pve/&page=4

Short version

To create a PMK you have to acquire a Core through purchase or stealing, it has to be stored on the ground on your token tile. Needs to be stored at the capital of your PMK after it’s formed. Owning this gives you the right to create/own a PMK. If you lose it you have 14 days to reclaim it or you lose that right. Each Core will be tracked automatically on the Zenath website.


Limitations

    Limit to 3 PMK’s at most at the same time.
    Initial 3 PMK’s are first come first serve at a cost of 10 gold.
    If more than one party is interested in buying the same Core, an auction over 2 days will be held.


Core storage requirements

    After purchase you will be provided a PMK Core item that is a heavy but loadable item, this is required to be stored on the ground at your token tile of the capital of your PMK.
    Should this Core item be lost (through a raid or spies/thieves, does not matter) you have 14 days to reclaim the item before the PMK gets force disbanded and the new owner gets to create theirs.
    If at any point it is found that the Core is not stored properly, you will have at least 24 hours warning before a force disband on all villages in the PMK.
    You are allowed to change the capital and thus where you store the Core once a month at most.


Stealing a Core

    Anyone that is out to steal a PMK Core needs to follow the same storage rules. It has to be kept on the token tile of the deed they want to become the capital of their new PMK during the 14 days wait. If this rule is broken they lose the right to the Core and it will be returned to the original owners.
    If you steal a second Core as a PMK and store it on what is already a PMK capital you will forfeit the second PMK after the 14 days are up, returning the Core back to the pool to be purchased again.


PMK Disbanding

    If you have lost the Core and have been unable to reclaim it during the 14 day reclaim period, GM’s will force disband all deeds that are part of the PMK as soon as they are able to [1].
    If your previous Core have been returned to the pool, you are allowed to participate in the purchase of it, giving a small extension to the disband period if it’s an auction.


Note: As some parts of this is going to be managed through GM interaction, small reasonable concessions can be made because of real life getting in the way. If this is abused the penalties will be very severe and decided on a case by case basis. The things requiring GM interaction may also have a slight delay if real life gets in the way, so we ask for your patience there. If you notice anyone breaking rules and want to accuse them of that, you will need proof in the form of screenshots/videos clearly showing it.

[1] We will try and do this when someone is online from the "victim" PMK so they have the chance to re-deed, you are also free to leave the PMK during the 14 days time to avoid the forced disband as well as being allowed to purchase a different core to replace the lost one. 
*/

package com.friya.wurmonline.server.pmk;

import java.util.Properties;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerPollListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import com.wurmonline.server.Servers;

/* TODO some time in the future (for now we just do these 'rare' events manually):
 	-	force abdicate ruler of a default kingdom
	-	make elector pick whomever it talks to first
	-	destroy cobra/stone/lady
			[] 62: Lady of the lake
			[] 63: Cobra King
			[] 538: stone of the sword
	-	disband all deeds in a pmk
	-	based on status of a core, explain "What happens next" on each core (i.e. "core pending destruction and respawning by GM")
*/

public class Mod implements WurmServerMod, Initable, Configurable, ServerStartedListener, PreInitable, ItemTemplatesCreatedListener, ServerPollListener
{
	//private static Logger logger = Logger.getLogger(Mod.class.getName());

	static String updateStatusUrl = "http://example.com/kingdomcores/update.php";
	static String updateStatusSecret = "thisisasecret";


	@Override
	public void configure(Properties properties)
	{
   		updateStatusUrl = String.valueOf(properties.getProperty("updateStatusUrl", String.valueOf(updateStatusUrl)));
   		updateStatusSecret = String.valueOf(properties.getProperty("updateStatusSecret", String.valueOf(updateStatusSecret)));
	}


	@Override
	public void preInit()
	{
	}


	@Override
	public void init()
	{
		WurmServerMod.super.init();
		ModActions.init();
	}


	@Override
	public void onItemTemplatesCreated()
	{
		SetupItemKingdomCore.onItemTemplatesCreated();
	}


	@Override
	public void onServerStarted()
	{
		ModActions.registerAction(new ActionSpawnKingdomCore());
		
		// Just make sure we have at least instantiated KingdomCores (will trigger a load of them).
		KingdomCores.getInstance();
	}


	@Override
	public void onServerPoll()
	{
		KingdomCores.getInstance().pollCores();
	}


	static public boolean isTestEnv()
	{
		return Servers.localServer.getName().equals("Friya");
	}
}
