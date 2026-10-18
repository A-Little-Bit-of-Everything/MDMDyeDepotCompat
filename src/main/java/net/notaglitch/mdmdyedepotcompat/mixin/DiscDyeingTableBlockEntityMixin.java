package net.notaglitch.mdmdyedepotcompat.mixin;

import com.kuronami.musicdiscmaker.block.DiscDyeingTableBlockEntity;
import com.kuronami.musicdiscmaker.color.DiscDye;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Redirects the {@code dyeOf(ItemStack)} call inside
 * {@link DiscDyeingTableBlockEntity} so that Dye Depot dye items (whose
 * {@link DyeColor} names are not in {@link DiscDye}'s built-in list) are
 * mapped to an appropriate {@link DiscDye} entry.
 *
 * <p>Strategy: maintain a hand-crafted map of Dye Depot colour name →
 * the closest vanilla {@link DiscDye} by hue/lightness, so no fragile
 * enum-constructor reflection is needed.
 */
@Mixin(value = DiscDyeingTableBlockEntity.class, remap = false)
public abstract class DiscDyeingTableBlockEntityMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Maps each Dye Depot dye name (as returned by {@link DyeColor#getName()})
     * to the closest vanilla {@link DiscDye} by visual appearance.
     */
    private static final Map<String, String> DD_TO_DISC_DYE = new HashMap<>();

    static {
        // Warm reds / pinks
        DD_TO_DISC_DYE.put("maroon",  "red");
        DD_TO_DISC_DYE.put("rose",    "pink");
        DD_TO_DISC_DYE.put("coral",   "pink");
        // Oranges / yellows
        DD_TO_DISC_DYE.put("amber",   "orange");
        DD_TO_DISC_DYE.put("ginger",  "orange");
        DD_TO_DISC_DYE.put("tan",     "orange");
        DD_TO_DISC_DYE.put("beige",   "white");
        // Greens
        DD_TO_DISC_DYE.put("olive",   "green");
        DD_TO_DISC_DYE.put("forest",  "green");
        DD_TO_DISC_DYE.put("verdant", "lime");
        DD_TO_DISC_DYE.put("mint",    "lime");
        // Blues / cyans / teals
        DD_TO_DISC_DYE.put("teal",    "cyan");
        DD_TO_DISC_DYE.put("aqua",    "cyan");
        DD_TO_DISC_DYE.put("navy",    "blue");
        // Purples / blues
        DD_TO_DISC_DYE.put("indigo",  "purple");
        DD_TO_DISC_DYE.put("slate",   "light_gray");
    }

    /**
     * Redirects the {@code DiscDye.byId(String)} call inside
     * {@code DiscDyeingTableBlockEntity.dyeOf(ItemStack)}.
     *
     * <p>When the colour name is a Dye Depot name that {@link DiscDye#byId}
     * would return {@code null} for, we substitute the mapped vanilla name
     * before calling {@code byId}.
     */
    @Redirect(
        method = "dyeOf(Lnet/minecraft/world/item/ItemStack;)Lcom/kuronami/musicdiscmaker/color/DiscDye;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/kuronami/musicdiscmaker/color/DiscDye;byId(Ljava/lang/String;)Lcom/kuronami/musicdiscmaker/color/DiscDye;"
        )
    )
    private static DiscDye mdmddc$byIdWithDyeDepot(String id) {
        DiscDye result = DiscDye.byId(id);
        if (result == null) {
            String mapped = DD_TO_DISC_DYE.get(id);
            if (mapped != null) {
                result = DiscDye.byId(mapped);
                LOGGER.debug("[MDMDyeDepotCompat] Mapped Dye Depot colour '{}' → DiscDye '{}'", id, mapped);
            }
        }
        return result;
    }
}
