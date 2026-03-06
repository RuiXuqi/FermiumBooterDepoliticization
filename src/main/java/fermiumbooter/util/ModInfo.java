package fermiumbooter.util;

public class ModInfo {
    public final String modId;
    public final String modName;
    public final String version;

    public ModInfo(String modid, String version, String name) {
        this.modId = modid;
        this.modName = name;
        this.version = version;
    }
}
