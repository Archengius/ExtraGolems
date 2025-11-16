package me.archengius.extra_golems;

import me.archengius.extra_golems.ai.ExtraGolemsMemoryModuleTypes;
import me.archengius.extra_golems.definition.CopperGolemDefinitions;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtraGolemsMod implements ModInitializer {
	public static final String MOD_ID = "extra_golems";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ExtraGolemsMemoryModuleTypes.register();
        CopperGolemDefinitions.register();
	}
}