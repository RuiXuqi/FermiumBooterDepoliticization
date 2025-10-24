package fermiumbooter;

import org.apache.logging.log4j.Level;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class FermiumLate implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        List<String> mixin = new ArrayList<>();
        //Start FermiumBooter section

        for(Map.Entry<String, List<Supplier<Boolean>>> entry : FermiumRegistryAPI.getLateMixins().entrySet()) {
            //Check for removals
            if(FermiumRegistryAPI.getRejectMixins().contains(entry.getKey())) {
                FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter received removal of \"{}\" for late mixin application, rejecting.", entry.getKey());
                continue;
            }
            //Check for enabled
            boolean enabled = false;
            for(Supplier<Boolean> supplier : entry.getValue()) {
                Boolean supplied = supplier.get();
                if(supplied == null) {
                    FermiumPlugin.LOGGER.log(Level.WARN, "FermiumBooter received null value from late application supplier for \"{}\".", entry.getKey());
                }
                else enabled |= supplied;
            }
            //Add configuration
            if(enabled) {
                FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter adding \"{}\" for late mixin application.", entry.getKey());
                mixin.add(entry.getKey());
            }
        }

        //Force clear the maps
        FermiumRegistryAPI.clear();

        //End FermiumBooter section

        return mixin;
    }
}
