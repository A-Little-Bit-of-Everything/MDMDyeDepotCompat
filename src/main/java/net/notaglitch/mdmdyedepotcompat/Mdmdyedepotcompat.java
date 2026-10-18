package net.notaglitch.mdmdyedepotcompat;

import com.kuronami.musicdiscmaker.color.DiscDye;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

@Mod(Mdmdyedepotcompat.MODID)
public class Mdmdyedepotcompat {

    public static final String MODID = "mdmdyedepotcompat";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Dye Depot colours to inject into {@link DiscDye#VALUES}.
     * Format: { id, hue, saturation, lightness }
     * HSL derived from each dye's primary RGB in DDDyes.
     */
    private static final Object[][] DYE_DEPOT_COLORS = {
        { "amber",    48.84,  100.00,  42.16 },
        { "aqua",    165.21,   82.95,  65.49 },
        { "beige",    48.39,   50.82,  76.08 },
        { "coral",    13.78,   67.84,  60.98 },
        { "forest",  114.24,   62.19,  39.41 },
        { "ginger",   22.07,   72.50,  47.06 },
        { "indigo",  262.11,   48.72,  22.94 },
        { "maroon",   11.54,   73.24,  27.84 },
        { "mint",    147.60,   60.48,  51.37 },
        { "navy",    209.62,   65.29,  23.73 },
        { "olive",    61.78,   54.59,  36.27 },
        { "rose",    357.76,  100.00,  68.43 },
        { "slate",   221.38,   27.62,  41.18 },
        { "tan",      25.03,   87.28,  66.08 },
        { "teal",    164.21,   44.71,  33.33 },
        { "verdant", 104.78,   62.62,  20.98 },
    };

    public Mdmdyedepotcompat(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::injectDiscDyeColors);
    }

    /**
     * Adds the Dye Depot colours to {@link DiscDye#VALUES}.
     *
     * <p>Java forbids {@code Constructor.newInstance()} on enum types
     * ("Cannot reflectively create enum objects"), so we allocate the
     * instances with {@link Unsafe#allocateInstance(Class)} — which does not
     * invoke any constructor — and then populate the private final fields
     * ({@code id}, {@code hue}, {@code saturation}, {@code lightness}) plus the
     * {@link Enum} base fields ({@code name}, {@code ordinal}) via reflection.
     */
    private void injectDiscDyeColors() {
        try {
            Unsafe unsafe = getUnsafe();

            // ── Resolve all fields; we write them via Unsafe field offsets so that
            //    the Java module system cannot block access (notably java.lang.Enum
            //    which lives in the locked-down java.base module). ─────────────────
            Field idField    = DiscDye.class.getDeclaredField("id");
            Field hueField   = DiscDye.class.getDeclaredField("hue");
            Field satField   = DiscDye.class.getDeclaredField("saturation");
            Field lightField = DiscDye.class.getDeclaredField("lightness");

            // Enum base fields (name + ordinal) live on java.lang.Enum
            Field nameField    = Enum.class.getDeclaredField("name");
            Field ordinalField = Enum.class.getDeclaredField("ordinal");

            long idOffset      = unsafe.objectFieldOffset(idField);
            long hueOffset     = unsafe.objectFieldOffset(hueField);
            long satOffset     = unsafe.objectFieldOffset(satField);
            long lightOffset   = unsafe.objectFieldOffset(lightField);
            long nameOffset    = unsafe.objectFieldOffset(nameField);
            long ordinalOffset = unsafe.objectFieldOffset(ordinalField);

            // ── Read current VALUES via plain reflection (DiscDye is in our module
            //    context, so setAccessible works — only java.lang.Enum fields are
            //    blocked, which is why those use Unsafe below). ───────────────────
            Field valuesField = DiscDye.class.getDeclaredField("VALUES");
            valuesField.setAccessible(true);
            DiscDye[] current = (DiscDye[]) valuesField.get(null);

            int baseOrdinal = current.length;
            java.util.List<DiscDye> newEntries = new java.util.ArrayList<>();

            for (Object[] color : DYE_DEPOT_COLORS) {
                String id = (String) color[0];

                boolean exists = false;
                for (DiscDye d : current) {
                    if (d.id().equals(id)) { exists = true; break; }
                }
                if (exists) continue;

                // Allocate without calling the constructor
                DiscDye entry = (DiscDye) unsafe.allocateInstance(DiscDye.class);

                // Populate Enum base state via Unsafe (module-check-free)
                unsafe.putObject(entry, nameOffset, id.toUpperCase(java.util.Locale.ROOT));
                unsafe.putInt(entry, ordinalOffset, baseOrdinal + newEntries.size());

                // Populate DiscDye state via Unsafe
                unsafe.putObject(entry, idOffset, id);
                unsafe.putDouble(entry, hueOffset, (double) color[1]);
                unsafe.putDouble(entry, satOffset, (double) color[2]);
                unsafe.putDouble(entry, lightOffset, (double) color[3]);

                newEntries.add(entry);
            }

            if (newEntries.isEmpty()) {
                LOGGER.info("[MDMDyeDepotCompat] All Dye Depot colours already present in DiscDye.VALUES – nothing to do.");
                return;
            }

            // ── Write extended array back ──────────────────────────────────────
            DiscDye[] extended = (DiscDye[]) Array.newInstance(DiscDye.class, current.length + newEntries.size());
            System.arraycopy(current, 0, extended, 0, current.length);
            for (int i = 0; i < newEntries.size(); i++) {
                extended[current.length + i] = newEntries.get(i);
            }
            valuesField.set(null, extended);

            LOGGER.info("[MDMDyeDepotCompat] Injected {} Dye Depot colour(s) into DiscDye.VALUES.", newEntries.size());

        } catch (Throwable t) {
            LOGGER.error("[MDMDyeDepotCompat] Failed to inject Dye Depot colours into DiscDye.VALUES. " +
                    "The @Redirect fallback will still provide approximate colour support.", t);
        }
    }

    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }
}
