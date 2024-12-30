package fermiumbooter;

import java.util.*;
import java.util.function.BooleanSupplier;

import com.google.common.eventbus.Subscribe;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;

@Deprecated
public class FermiumBooter extends net.minecraftforge.fml.common.DummyModContainer implements zone.rong.mixinbooter.ILateMixinLoader
{
    public static final String MODID = "fermiumbooter";
    public static final String VERSION = "1.1.1";
    public static final String NAME = "FermiumBooterDepoliticization";

	public FermiumBooter() {
		super(new ModMetadata());
        ModMetadata metadata = this.getMetadata();
        metadata.modId = MODID;
        metadata.name = NAME;
        metadata.authorList.add("Hileb");
        metadata.version = VERSION;
        metadata.credits = "\n" +
            "       MixinBooter - it is a true mixin lib with tech.\n";
        metadata.description = "Defenders against destroying communities";
        metadata.url = "https://github.com/Ecdcaeb/FermiumBooterDepoliticization";
        metadata.logoFile ="assets/fermiumbooter/icon.png";
	}

	@Override
    public boolean registerBus(com.google.common.eventbus.EventBus bus, net.minecraftforge.fml.common.LoadController controller) {  
        bus.register(this);
		return true;
    }

	@Subscribe
	@SuppressWarnings("unused")
	public void onConstructed(FMLConstructionEvent event) {
		FermiumRegistryAPI.clear();
	}

    @Override
    public Set<net.minecraftforge.fml.common.versioning.ArtifactVersion> getRequirements() {
        return Collections.singleton(new net.minecraftforge.fml.common.versioning.DefaultArtifactVersion("mixinbooter"));
    }

    @Override
    public List<String> getMixinConfigs(){
		return Arrays.asList(FermiumRegistryAPI.getLateMixins().keySet().toArray(new String[0]));
	}

	@Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if (FermiumRegistryAPI.getRejectMixins().contains(mixinConfig)) {
			FermiumPlugin.LOGGER.debug("FermiumBooter received removal of \"" + mixinConfig + "\" for early mixin application, rejecting.");
			return false;
		} else {
            for(BooleanSupplier supplier : FermiumRegistryAPI.getLateMixins().get(mixinConfig)) {
                if (supplier.getAsBoolean()) {
                    FermiumPlugin.LOGGER.debug("FermiumBooter adding \"" + mixinConfig + "\" for early mixin application.");
                    return true;
                }
            }
            FermiumPlugin.LOGGER.debug("FermiumBooter received null value for suppliers from \"" + mixinConfig + "\" for early mixin application, ignoring.");
            return false;
        }
    }
}