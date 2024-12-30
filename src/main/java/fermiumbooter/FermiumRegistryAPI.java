package fermiumbooter;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/**
 * Enqueue mixins to be applied or rejected from your IFMLLoadingPlugin class init
 * Includes options for disabling the mixin from a Supplier, and loading it either early or late
 * Configuration name is the name of the json pointing to your mixin, such as "mixins.fermiumbooter.init.json"
 */
@Deprecated
public abstract class FermiumRegistryAPI {

    @Deprecated
    private static final Logger LOGGER = FermiumPlugin.LOGGER;

    // WTF? Why do not BooleanSupplier ?
    @Deprecated
    private static Multimap<String, BooleanSupplier> earlyMixins = HashMultimap.create();
    @Deprecated
    private static Multimap<String, BooleanSupplier> lateMixins = HashMultimap.create();
    @Deprecated
    private static Set<String> rejectMixins = new HashSet<>();

    /**
     * Register multiple mixin config resources at once to be applied
     * @param late - whether to apply the mixin late or early
     * @param configurations - mixin config resource names
     */
    @Deprecated
    public static void enqueueMixin(boolean late, String... configurations) {
        for(String configuration : configurations) {
            enqueueMixin(late, configuration);
        }
    }

    /**
     * Register a mixin config resource to be applied
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     */
    @Deprecated
    public static void enqueueMixin(boolean late, String configuration) {
        enqueueMixin(late, configuration, () -> true);
    }

    /**
     * Add a mixin config resource to be applied, with a toggle to apply or not
     * Note: I do not think this specific method is necessary, but it's here just in case
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     * @param enabled - whether to apply the mixin or not
     */
    @Deprecated
    public static void enqueueMixin(boolean late, String configuration, boolean enabled) {
        enqueueMixin(late, configuration, () -> enabled);
    }

    /**
     * Add a mixin config resource to be applied, with a supplier to toggle application to be evaluated after all like-timed configs are registered
     * Note: If multiple suppliers are given for a single configuration, it is evaluated as OR
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     * @param supplier - supplier to determine whether to apply the mixin or not
     */
    @Deprecated
    public static void enqueueMixin(boolean late, String configuration, BooleanSupplier supplier) {
        checkState();
        if(configuration == null || configuration.trim().isEmpty()) {
            LOGGER.debug("FermiumRegistryAPI supplied null or empty configuration name during mixin enqueue, ignoring.");
            return;
        }
        if(supplier == null) {//Do not evaluate supplier.get() itself for null now
            LOGGER.warn("FermiumRegistryAPI supplied null supplier for configuration \"" + configuration + "\" during mixin enqueue, ignoring.");
            return;
        }
        //Process rejects prior to application
        if(late) {
            LOGGER.debug("FermiumRegistryAPI supplied \"" + configuration + "\" for late mixin enqueue, adding.");
            lateMixins.put(configuration, supplier);
        }
        else {
            LOGGER.debug("FermiumRegistryAPI supplied \"" + configuration + "\" for early mixin enqueue, adding.");
            earlyMixins.put(configuration, supplier);
        }
    }

    /**
     * Add a mixin config resource to be applied, with a supplier to toggle application to be evaluated after all like-timed configs are registered
     * Note: If multiple suppliers are given for a single configuration, it is evaluated as OR
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     * @param supplier - supplier to determine whether to apply the mixin or not
     */
    @Deprecated
    public static void enqueueMixin(boolean late, String configuration, Supplier<Boolean> supplier) {
        enqueueMixin(late, configuration, () -> supplier.get());
    }

    /**
     * Designates a mixin config resource name to be ignored before application (Will only affect FermiumBooter applied mixins)
     * Note: Realistically you should not use this, but it is provided in the case of specific tweaker mod needs
     * @param configuration - mixin config resource name
     */
    @Deprecated
    public static void removeMixin(String configuration) {
        checkState();
        if(configuration == null || configuration.trim().isEmpty()) {
            LOGGER.debug("FermiumRegistryAPI supplied null or empty configuration name for mixin removal, ignoring.");
            return;
        }
        LOGGER.debug("FermiumRegistryAPI supplied \"" + configuration + "\" for mixin removal, adding.");
        rejectMixins.add(configuration);
    }

    /**
     * Internal Use; Do Not Use
     */
    @Deprecated
    public static Multimap<String, BooleanSupplier> getEarlyMixins() {
        return earlyMixins;
    }

    /**
     * Internal Use; Do Not Use
     */
    @Deprecated
    public static Multimap<String, BooleanSupplier> getLateMixins() {
        return lateMixins;
    }

    /**
     * Internal Use; Do Not Use
     */
    @Deprecated
    public static Set<String> getRejectMixins() {
        return rejectMixins;
    }

    private static void checkState() {
        if (earlyMixins == null) throw new IllegalStateException("Mixins should be registered before ModConstruction");
        if (lateMixins == null) throw new IllegalStateException("Mixins should be registered before ModConstruction");
        if (rejectMixins == null) throw new IllegalStateException("Mixins should be registered before ModConstruction");
    }

    /**
     * Internal Use; Do Not Use
     */
    @Deprecated
    public static void clear() {
        // :)
        earlyMixins = null;
        lateMixins = null;
        rejectMixins = null;
    }
}