package fermiumbooter;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Deprecated
@IFMLLoadingPlugin.Name("FermiumBooter")
@IFMLLoadingPlugin.SortingIndex(990)
public class FermiumPlugin
    implements IFMLLoadingPlugin, zone.rong.mixinbooter.IEarlyMixinLoader {
  public static final Logger LOGGER =
      LogManager.getLogger("FermiumBooterDepoliticization");

  public static File source = null;

  @Override
  public String[] getASMTransformerClass() {
    return null;
  }
  @Override
  public String getModContainerClass() {
    return "fermiumbooter.FermiumBooter";
  }
  @Override
  public String getSetupClass() {
    return null;
  }
  @Override
  public String getAccessTransformerClass() {
    return null;
  }
  @Override
  public void injectData(Map<String, Object> data) {
    source = (File) data.get("coremodLocation");
    if (source != null)
      makeFMLCorePluginContainsFMLMod(source);
  }

  public static void makeFMLCorePluginContainsFMLMod(File file) {
    String name = file.getName();
    CoreModManager.getIgnoredMods().remove(name);
    CoreModManager.getReparseableCoremods().add(name);
  }

  @Override
  public List<String> getMixinConfigs() {
    return Arrays.asList(
        FermiumRegistryAPI.getEarlyMixins().keySet().toArray(new String[0]));
  }

  @Override
  public boolean shouldMixinConfigQueue(String mixinConfig) {
    if (FermiumRegistryAPI.getRejectMixins().contains(mixinConfig)) {
      FermiumPlugin.LOGGER.debug("FermiumBooter received removal of \""
          + mixinConfig + "\" for early mixin application, rejecting.");
      return false;
    } else {
      for (BooleanSupplier supplier :
          FermiumRegistryAPI.getEarlyMixins().get(mixinConfig)) {
        if (supplier.getAsBoolean()) {
          FermiumPlugin.LOGGER.debug("FermiumBooter adding \"" + mixinConfig
              + "\" for early mixin application.");
          return true;
        }
      }
      FermiumPlugin.LOGGER.debug(
          "FermiumBooter received null value for suppliers from \""
          + mixinConfig + "\" for early mixin application, ignoring.");
      return false;
    }
  }
}