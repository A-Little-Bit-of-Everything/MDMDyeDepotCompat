package net.notaglitch.mdmdyedepotcompat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Mdmdyedepotcompat.MODID)
public class Mdmdyedepotcompat {

    public static final String MODID = "mdmdyedepotcompat";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Mdmdyedepotcompat(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("MDM Dye Depot Compat loaded – disc dyeing table now supports Dye Depot colors.");
    }
}
