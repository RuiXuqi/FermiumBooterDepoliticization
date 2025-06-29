package fermiumbooter.internal;

@net.minecraftforge.common.config.Config(modid = "fermiumbooter")
public class FBConfig {
	
	@net.minecraftforge.common.config.Config.Name("Override Mixin Config Compatibility Checks")
	@net.minecraftforge.common.config.Config.RequiresMcRestart
	public static boolean overrideMixinCompatibilityChecks = false;
}