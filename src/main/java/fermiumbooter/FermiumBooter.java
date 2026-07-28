package fermiumbooter;

import fermiumbooter.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = FermiumBooter.MODID, version = FermiumBooter.VERSION, name = FermiumBooter.NAME)
public class FermiumBooter {
	
    public static final String MODID = Reference.MOD_ID;
    public static final String VERSION = Reference.VERSION;
    public static final String NAME = Reference.MOD_NAME;
	
	@SidedProxy(clientSide = "fermiumbooter.proxy.ClientProxy", serverSide = "fermiumbooter.proxy.CommonProxy")
	public static CommonProxy PROXY;
	
	@Instance(MODID)
	public static FermiumBooter instance;
	
	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		FermiumBooter.PROXY.registerSubscribers();
	}
}