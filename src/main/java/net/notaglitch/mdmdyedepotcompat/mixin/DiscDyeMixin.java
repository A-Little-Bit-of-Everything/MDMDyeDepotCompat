package net.notaglitch.mdmdyedepotcompat.mixin;

import com.kuronami.musicdiscmaker.color.DiscDye;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Injects the 16 extra Dye Depot colours into {@link DiscDye#VALUES} so that the
 * Music Disc Maker disc-dyeing table accepts Dye Depot dye items.
 *
 * <p>How it works:
 * <ol>
 *   <li>Dye Depot adds new {@link net.minecraft.world.item.DyeColor} enum entries
 *       (amber, teal, …) via its own {@code DyeColorMixin}.</li>
 *   <li>Music Disc Maker's {@code DiscDyeingTableBlockEntity.dyeOf()} gets the
 *       vanilla/extended {@link net.minecraft.world.item.DyeColor#getName()} and
 *       passes it to {@link DiscDye#byId(String)}, which searches {@code VALUES}.</li>
 *   <li>Because {@code DiscDye} is a {@code final enum} its constant list is fixed
 *       at class-load time, but the separate {@code VALUES} array (which {@code byId}
 *       reads) is just a normal {@code static} field. We can widen it with new entries
 *       by constructing them reflectively via the private enum constructor.</li>
 * </ol>
 *
 * <p>The HSL values below were derived from the dye_depot primary RGB color of each
 * Dye Depot dye. They are only used by Music Disc Maker to tint the disc label art –
 * any reasonable approximation produces good results.
 */
@Mixin(value = DiscDye.class, remap = false)
public abstract class DiscDyeMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Gives us write access to the {@code VALUES} field that {@link DiscDye#byId}
     * iterates. This field is distinct from the compiler-generated {@code $VALUES}.
     */
    @Shadow
    private static DiscDye[] VALUES;

    /**
     * New colour entries to register, one per Dye Depot dye.
     * Format: { id, hue, saturation, lightness }
     * HSL computed from each dye's primary hex colour in DDDyes.
     */
    private static final Object[][] DYE_DEPOT_COLORS = {
        // name        hue      sat      light
        { "amber",    48.84,  100.00,   42.16 },
        { "aqua",    165.21,   82.95,   65.49 },
        { "beige",    48.39,   50.82,   76.08 },
        { "coral",    13.78,   67.84,   60.98 },
        { "forest",  114.24,   62.19,   39.41 },
        { "ginger",   22.07,   72.50,   47.06 },
        { "indigo",  262.11,   48.72,   22.94 },
        { "maroon",   11.54,   73.24,   27.84 },
        { "mint",    147.60,   60.48,   51.37 },
        { "navy",    209.62,   65.29,   23.73 },
        { "olive",    61.78,   54.59,   36.27 },
        { "rose",    357.76,  100.00,   68.43 },
        { "slate",   221.38,   27.62,   41.18 },
        { "tan",      25.03,   87.28,   66.08 },
        { "teal",    164.21,   44.71,   33.33 },
        { "verdant", 104.78,   62.62,   20.98 },
    };

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mdmddc$injectDyeDepotColors(CallbackInfo ci) {
        try {
            // Locate the private enum constructor: (String name, int ordinal, String id, double hue, double sat, double light)
            Constructor<DiscDye> ctor = DiscDye.class.getDeclaredConstructor(
                    String.class, int.class, String.class, double.class, double.class, double.class);
            ctor.setAccessible(true);

            // Work out the next ordinal (enum ordinals start at 0, so size == next ordinal)
            int baseOrdinal = VALUES.length;

            // Collect only the colors that aren't already registered (prevents double-injection)
            java.util.List<DiscDye> newEntries = new java.util.ArrayList<>();
            for (Object[] color : DYE_DEPOT_COLORS) {
                String id = (String) color[0];
                boolean alreadyExists = false;
                for (DiscDye existing : VALUES) {
                    if (existing.id().equals(id)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    // The enum name (first String arg) is upper-case by convention
                    String enumName = id.toUpperCase(java.util.Locale.ROOT);
                    double hue  = (double) color[1];
                    double sat  = (double) color[2];
                    double light = (double) color[3];
                    DiscDye entry = ctor.newInstance(enumName, baseOrdinal + newEntries.size(), id, hue, sat, light);
                    newEntries.add(entry);
                }
            }

            if (newEntries.isEmpty()) return;

            // Replace VALUES with an expanded array
            DiscDye[] extended = (DiscDye[]) Array.newInstance(DiscDye.class, VALUES.length + newEntries.size());
            System.arraycopy(VALUES, 0, extended, 0, VALUES.length);
            for (int i = 0; i < newEntries.size(); i++) {
                extended[VALUES.length + i] = newEntries.get(i);
            }

            // Write back via the @Shadow field (Mixin will resolve the real field reference)
            VALUES = extended;

            LOGGER.info("[MDMDyeDepotCompat] Injected {} Dye Depot color(s) into DiscDye.VALUES.", newEntries.size());
        } catch (Exception e) {
            LOGGER.error("[MDMDyeDepotCompat] Failed to inject Dye Depot colors into DiscDye.VALUES. " +
                    "Dye Depot dyes will not work in the disc-dyeing table.", e);
        }
    }
}
