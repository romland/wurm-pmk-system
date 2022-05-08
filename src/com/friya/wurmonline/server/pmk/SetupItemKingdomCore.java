package com.friya.wurmonline.server.pmk;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.shared.constants.IconConstants;
import com.wurmonline.shared.constants.ItemMaterials;


public class SetupItemKingdomCore implements ItemTypes, MiscConstants, ItemMaterials
{
	private static Logger logger = Logger.getLogger(SetupItemKingdomCore.class.getName());
	private static int itemId;


	static public int getId()
	{
		return itemId;
	}
	
	
	public int getTemplateId()
	{
		return getId();
	}


	static public void onItemTemplatesCreated()
	{
		try {
			ItemTemplateBuilder itemTemplateBuilder = new ItemTemplateBuilder("friya.pmkcore3");
			itemTemplateBuilder.name("kingdom core", "kingdom cores", 
				"This is a Kingdom Core. Owning one grants the kingdom the right to have a ruler and offices. The Kingdom Core *must* be stored on a village token, or the granted right will be revoked. It can be stolen and loaded onto vehicles. It cannot be recharged."
			);
			itemTemplateBuilder.descriptions("excellent", "good", "ok", "poor");
			
			itemTemplateBuilder.itemTypes(new short[] { 
					ITEM_TYPE_NAMED,
					ITEM_TYPE_HASDATA,
					ITEM_TYPE_DECORATION,
					ITEM_TYPE_USE_GROUND_ONLY,
					ITEM_TYPE_INDESTRUCTIBLE,
					ITEM_TYPE_TRANSPORTABLE,			// Loadable
					ITEM_TYPE_NODROP,
					ITEM_TYPE_NOMOVE,
					ITEM_TYPE_NOPUT,
					ITEM_TYPE_NORENAME,
					ITEM_TYPE_NOTAKE,
					ITEM_TYPE_NOT_SPELL_TARGET,
//					ITEM_TYPE_ONE_PER_TILE,				// enabling this means you cannot have two cores on the token
					ITEM_TYPE_FOUR_PER_TILE,
//					ITEM_TYPE_ROYAL						// This will hopefully prevent the item from leaving the server (actually, it will, but it won't end up in world then!)
					ITEM_TYPE_ARTIFACT					// This adds to description, so preferably not have it
			});
			itemTemplateBuilder.imageNumber((short) IconConstants.ICON_ARTIFACT_VALREI);
			itemTemplateBuilder.behaviourType((short) 1);
			itemTemplateBuilder.combatDamage(0);
			itemTemplateBuilder.decayTime(3024000L);
			itemTemplateBuilder.dimensions(100, 100, 50);
			itemTemplateBuilder.bodySpaces(new byte[]{});
			itemTemplateBuilder.modelName("model.valrei.magic.");
			itemTemplateBuilder.weightGrams(15000);
			itemTemplateBuilder.material(MATERIAL_STONE);
			itemTemplateBuilder.difficulty(90.0f);
			itemTemplateBuilder.primarySkill(-10);

			ItemTemplate tpl = itemTemplateBuilder.build();

			itemId = tpl.getTemplateId();
			logger.log(Level.INFO, "Using template id " + itemId);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

        logger.log(Level.INFO, "Setup completed");
	}
}
