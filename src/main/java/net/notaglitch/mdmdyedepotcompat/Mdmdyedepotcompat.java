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

/**
 * Compat mod that lets the Music Disc Maker disc-dyeing table accept Dye Depot dyes
 * AND give each Dye Depot color a distinct disc tint.
 *
 * <p>Two things must happen, both done here in {@link FMLCommonSetupEvent}:
 * <ol>
 *   <li><b>Inject the 16 Dye Depot colors into {@code DiscDye.VALUES}</b> so that
 *       {@code DiscDye.byId()} resolves them and {@code DiscPalette} (which tints
 *       purely from each dye's HSL fields) produces a unique color.</li>
 *   <li><b>Resize {@code DiscDyeTint}'s caches.</b> That class snapshots
 *       {@code DiscDye.values()} (16) into {@code DYES}/{@code DYE_COUNT} and sizes
 *       {@code CUSTOM_CACHE} as {@code DYE_COUNT²} = 256, then indexes it by
 *       {@code board.ordinal() * DYE_COUNT + accent.ordinal()}. Our injected dyes
 *       have ordinals 16–31, which overflow the 256-slot cache (the crash seen in
 *       earlier builds). We rebuild those caches sized to the new dye count.</li>
 * </ol>
 *
 * <p>Enum instances can't be created via a constructor
 * ("Cannot reflectively create enum objects"), so we use
 * {@link Unsafe#allocateInstance(Class)} and populate fields via Unsafe offsets
 * (which also bypass the module system's block on {@code java.lang.Enum} fields).
 */
@Mod(Mdmdyedepotcompat.MODID)
public class Mdmdyedepotcompat {

    public static final String MODID = "mdmdyedepotcompat";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** { id, hue, saturation, lightness } per Dye Depot dye (HSL from DDDyes RGB). */
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
        event.enqueueWork(() -> {
            if (injectDiscDyeColors()) {
                // DiscDyeTint is a client-only class; only touch it on the client.
                if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    resizeDiscDyeTintCaches();
                }
            }
        });
    }

    /** @return true if VALUES now contains the Dye Depot colors (either we added them or they were already present). */
    private boolean injectDiscDyeColors() {
        try {
            Unsafe unsafe = getUnsafe();

            Field idField    = DiscDye.class.getDeclaredField("id");
            Field hueField   = DiscDye.class.getDeclaredField("hue");
            Field satField   = DiscDye.class.getDeclaredField("saturation");
            Field lightField = DiscDye.class.getDeclaredField("lightness");
            Field nameField    = Enum.class.getDeclaredField("name");
            Field ordinalField = Enum.class.getDeclaredField("ordinal");

            long idOffset      = unsafe.objectFieldOffset(idField);
            long hueOffset     = unsafe.objectFieldOffset(hueField);
            long satOffset     = unsafe.objectFieldOffset(satField);
            long lightOffset   = unsafe.objectFieldOffset(lightField);
            long nameOffset    = unsafe.objectFieldOffset(nameField);
            long ordinalOffset = unsafe.objectFieldOffset(ordinalField);

            // Read VALUES via reflection (readable; it's the WRITE of a final field that needs Unsafe)
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

                DiscDye entry = (DiscDye) unsafe.allocateInstance(DiscDye.class);
                unsafe.putObject(entry, nameOffset, id.toUpperCase(java.util.Locale.ROOT));
                unsafe.putInt(entry, ordinalOffset, baseOrdinal + newEntries.size());
                unsafe.putObject(entry, idOffset, id);
                unsafe.putDouble(entry, hueOffset, (double) color[1]);
                unsafe.putDouble(entry, satOffset, (double) color[2]);
                unsafe.putDouble(entry, lightOffset, (double) color[3]);
                newEntries.add(entry);
            }

            if (newEntries.isEmpty()) {
                LOGGER.info("[MDMDyeDepotCompat] Dye Depot colors already present in DiscDye.VALUES.");
                return true;
            }

            DiscDye[] extended = (DiscDye[]) Array.newInstance(DiscDye.class, current.length + newEntries.size());
            System.arraycopy(current, 0, extended, 0, current.length);
            for (int i = 0; i < newEntries.size(); i++) {
                extended[current.length + i] = newEntries.get(i);
            }

            // VALUES is static final -> write via Unsafe static-field API
            Object valuesBase   = unsafe.staticFieldBase(valuesField);
            long   valuesOffset = unsafe.staticFieldOffset(valuesField);
            unsafe.putObject(valuesBase, valuesOffset, extended);

            DiscDye[] verify = (DiscDye[]) valuesField.get(null);
            LOGGER.info("[MDMDyeDepotCompat] Injected {} Dye Depot color(s); DiscDye.VALUES {} -> {}.",
                    newEntries.size(), current.length, verify.length);
            return true;

        } catch (Throwable t) {
            LOGGER.error("[MDMDyeDepotCompat] Failed to inject Dye Depot colors into DiscDye.VALUES.", t);
            return false;
        }
    }

    /**
     * Rebuilds {@code DiscDyeTint}'s snapshot fields so they agree with the now-larger
     * {@code DiscDye.VALUES}. Otherwise its 16×16 color cache overflows when indexed
     * by our ordinals 16–31 and crashes the dyeing-table screen.
     */
    private void resizeDiscDyeTintCaches() {
        try {
            Class<?> tintClass = Class.forName("com.kuronami.musicdiscmaker.client.DiscDyeTint");
            Unsafe unsafe = getUnsafe();

            DiscDye[] allDyes = DiscDye.values();   // the real enum constants (still 16 — $VALUES is untouched)
            // We index caches by ordinal, and our injected dyes have the highest ordinals,
            // so the cache must be sized by (maxOrdinal + 1), not by values().length.
            Field valuesField = DiscDye.class.getDeclaredField("VALUES");
            valuesField.setAccessible(true);
            DiscDye[] extended = (DiscDye[]) valuesField.get(null);
            int dyeCount = extended.length;

            // DYES field: replace with the extended list so automaticColors() can index it
            setStaticFinalObject(unsafe, tintClass.getDeclaredField("DYES"), extended);

            // DYE_COUNT (int): widen to the new count so colors()'s index math matches cache size
            Field dyeCountField = tintClass.getDeclaredField("DYE_COUNT");
            Object dcBase = unsafe.staticFieldBase(dyeCountField);
            long   dcOff  = unsafe.staticFieldOffset(dyeCountField);
            unsafe.putInt(dcBase, dcOff, dyeCount);

            // CUSTOM_CACHE (int[][]): size dyeCount * dyeCount (board.ordinal()*DYE_COUNT + accent.ordinal())
            setStaticFinalObject(unsafe, tintClass.getDeclaredField("CUSTOM_CACHE"),
                    new int[dyeCount * dyeCount][]);

            // AUTO_ACCENT_CACHE (int[][]): size dyeCount
            setStaticFinalObject(unsafe, tintClass.getDeclaredField("AUTO_ACCENT_CACHE"),
                    new int[dyeCount][]);

            LOGGER.info("[MDMDyeDepotCompat] Resized DiscDyeTint caches to dye count {}.", dyeCount);

        } catch (Throwable t) {
            LOGGER.error("[MDMDyeDepotCompat] Failed to resize DiscDyeTint caches. " +
                    "Dyeing with Dye Depot colors may crash the screen – report this.", t);
        }
    }

    private static void setStaticFinalObject(Unsafe unsafe, Field field, Object value) {
        Object base = unsafe.staticFieldBase(field);
        long off = unsafe.staticFieldOffset(field);
        unsafe.putObject(base, off, value);
    }

    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }
}
