package fermiumbooter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import fermiumbooter.util.CustomLogger;
import fermiumbooter.util.FermiumJarScanner;
import fermiumbooter.util.ForcedConfigHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.mixinbooter.IEarlyMixinLoader;

@IFMLLoadingPlugin.Name("FermiumBooter")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(990)
public class FermiumPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

	public static final Logger LOGGER = LogManager.getLogger("FermiumBooter");
	
	static {
		//Handle forced mixins reasonably early to catch crashes
		ForcedConfigHandler.handleForcedMixinConfigs();
	}

	@Override
	public String[] getASMTransformerClass()
	{
		return new String[0];
	}
	
	@Override
	public String getModContainerClass()
	{
		return null;
	}
	
	@Override
	public String getSetupClass()
	{
		return null;
	}

    @Override
    public void injectData(Map<String, Object> map) {
    }

    /**
	 * Handle actually parsing and adding the early configurations here, as it gets called after all other plugins are initialized
	 */
	@Override
    public List<String> getMixinConfigs() {
        CustomLogger.init();

		//Handle caching now if it hasn't already
		FermiumJarScanner.handleCaching();
		//Clear larger cached jar scanner fields cause why not
		FermiumJarScanner.clearCaches();
        List<String> mixin = new ArrayList<>();

		for(Map.Entry<String, List<Supplier<Boolean>>> entry : FermiumRegistryAPI.getEarlyMixins().entrySet()) {
			//Check for removals
			if(FermiumRegistryAPI.getRejectMixins().contains(entry.getKey())) {
				LOGGER.log(Level.INFO, "FermiumBooter received removal of \"{}\" for early mixin application, rejecting.", entry.getKey());
				continue;
			}
			//Check for enabled
			boolean enabled = false;
			for(Supplier<Boolean> supplier : entry.getValue()) {
				Boolean supplied = supplier.get();
				if(supplied == null) {
					LOGGER.log(Level.WARN, "FermiumBooter received null value from early application supplier for \"{}\".", entry.getKey());
				}
				else enabled |= supplied;
			}
			//Add configuration
			if(enabled) {
				LOGGER.log(Level.INFO, "FermiumBooter adding \"{}\" for early mixin application.", entry.getKey());
				mixin.add(entry.getKey());
			}
		}
        return mixin;
	}
	
	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}