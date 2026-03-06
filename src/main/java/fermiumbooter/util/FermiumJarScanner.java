package fermiumbooter.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fermiumbooter.FermiumPlugin;
import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.config.FermiumBooterConfig;
import io.github.classgraph.*;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Handler for searching for modids and @MixinConfig handling
 * Searches for both mcmod.info and @Mod annotations to find modids, since some mods only have one or the other
 * Some also have neither or set it improperly and give me a headache, so they have manual compatibility
 * Also handles searching for and parsing @MixinConfig handling using ClassGraph to avoid classloading issues
 */
public abstract class FermiumJarScanner {

	private static final Logger LOGGER = LogManager.getLogger("FermiumJarScanner");

	private final static String modClassName = "net.minecraftforge.fml.common.Mod";

	private static final Map<String, ModInfo> presentMods = new HashMap<>();
	private static final Set<String> earlyModIDs = new HashSet<>();
	private static final Set<MixinConfigInfo> mixinToggles = new HashSet<>();
	private static int warningCount = 0;
	
	public static boolean isModPresent(String modID) {
		if(modID == null || modID.isEmpty()) return false;
		handleCaching();
		return presentMods.containsKey(modID);
	}
	
	public static void handleCaching() {
		if(!presentMods.isEmpty() || !mixinToggles.isEmpty()) return;
		
		LOGGER.log(Level.INFO, "FermiumJarScanner beginning jar searching.");
		startJarSearching();
		LOGGER.log(Level.INFO, "FermiumJarScanner finished jar searching, found {} ModIDs.", presentMods.size());
		
		LOGGER.log(Level.INFO, "FermiumMixinConfig beginning MixinConfig parsing.");
		for(MixinConfigInfo mixinConfig : mixinToggles)
			applyMixinConfig(mixinConfig);

		LOGGER.log(Level.INFO, "FermiumMixinConfig finished MixinConfig parsing, parsed {} config options with {} warnings", mixinToggles.size(), warningCount);
	}

	public static int getWarningCount() {
		return warningCount;
	}
	
	public static void clearCaches() {
		mixinToggles.clear();
		manualOverrides.clear();
		modConfigMap.clear();
	}
	
	//Not the most efficient implementation possible, but only takes around 2-3 seconds or so total even in relatively large packs
	private static void startJarSearching() {
		long time = System.currentTimeMillis();

		//Technically shows up as modids but isn't found through normal methods, just add manually
		presentMods.put("minecraft", new ModInfo("minecraft", "1.12.2", "Minecraft"));
		presentMods.put("mcp", new ModInfo("mcp", "9.42", "Minecraft Coder Pack"));
		presentMods.put("FML", new ModInfo("FML", null, "Forge Mod Loader"));
		presentMods.put("forge", new ModInfo("forge", null, "Minecraft Forge"));

		//Always feels wrong but it works
		File mcDir = new File(".");

		List<Artifact> maven_canidates = LibraryManager.flattenLists(mcDir);
		List<File> file_canidates = LibraryManager.gatherLegacyCanidates(mcDir);
		for(Artifact artifact : maven_canidates) {
			artifact = Repository.resolveAll(artifact);
			if(artifact != null) {
				File target = artifact.getFile();
				if(!file_canidates.contains(target)) {
					file_canidates.add(target);
				}
			}
		}

		for(File modFile : file_canidates) {
			searchJarFile(modFile); //search for config overridden special cases
		}

		//search for @Mod and @MixinConfig annotated classes
		List<String> mixinConfigPaths = new ArrayList<>();
		try (ScanResult scanResult = new ClassGraph()
				.enableAnnotationInfo()
				.disableModuleScanning()
				.overrideClasspath(
						mcDir.getAbsolutePath()+"/mods/*"+ File.pathSeparatorChar+
								Arrays.stream(Launch.classLoader.getURLs()).map(URL::getPath).collect(Collectors.joining(File.pathSeparator))
				)
				.rejectPackages(
						"java.*",
						"org.spongepowered.*",
						"net.minecraftforge.*",
						"net.minecraft.*",
						"com.google.common.*",
						"com.mojang.*",
						"org.objectweb.asm.*",
						"io.github.classgraph.*",
						"nonapi.io.github.classgraph.*",
						"com.llamalad7.*",
						"kotlin.*",
						"it.unimi*"
				)
				.scan()
		) {
			//mcmod.info search
			//Some mods are dumb and set their mcmod.info modids incorrectly, need to also check annotation regardless
			for(Resource resource : scanResult.getResourcesWithLeafName("mcmod.info")){
				try(InputStream inputStream = resource.open()) {
					InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
					JsonElement rootElement = new JsonParser().parse(reader);
					//Manually search instead of properly parsing to class to avoid classloading issues
					searchModInfoRecursive(rootElement);
				} catch(Exception ignored) {}
				finally {
					resource.close();
				}
			}

			//search for @Mod
			for(ClassInfo classInfo : scanResult.getClassesWithAnnotation(modClassName))
				parseMod(classInfo);

			//search for @MixinConfig
			for(ClassInfo classInfo : scanResult.getClassesWithAnnotation(MixinConfig.class.getName()))
				mixinConfigPaths.add(classInfo.getPackageName()+".*");
		}
		earlyModIDs.addAll(presentMods.keySet()); // just for mix2ferm

		//search @MixinConfig annotated classes more specifically
		try (ScanResult scanResult = new ClassGraph()
				.enableAnnotationInfo()
				.enableFieldInfo()
				.disableModuleScanning()
				.acceptPackages(mixinConfigPaths.toArray(new String[0])) //no need to change classPath cause these classes are from jars that have a coremod
				.scan()
		) {
			for(ClassInfo classInfo : scanResult.getClassesWithAnnotation(MixinConfig.class.getName()))
				parseMixinConfig(classInfo);
		}

		long elapsed = System.currentTimeMillis() - time;
		LOGGER.debug("Searching through present mods took {} ms", elapsed);
	}

	private static Map<String, String> manualOverrides = null;
	
	private static void searchJarFile(File modFile) {
		if(manualOverrides == null){
			manualOverrides = new HashMap<>();
			ConfigCategory cat = FermiumPlugin.CONFIG.getCategory("general.jar scanner manual overrides");
			if(cat != null)
				for(Map.Entry<String, Property> entry : cat.getValues().entrySet())
					manualOverrides.put(entry.getKey(), entry.getValue().getString());
		}

		//Search jar
		try(JarFile jar = new JarFile(modFile)) {
			//Iterate files in jar for manual overrides
			if(manualOverrides.isEmpty()) return;
			for(ZipEntry ze : Collections.list(jar.entries())) {
				//Skip irrelevant paths/files that waste time
				if(ze.getName().contains("__MACOSX")) continue;
				if(ze.getName().contains("module-info")) continue;
				if(ze.getName().startsWith("org/spongepowered")) continue;
				if(ze.getName().startsWith("com/llamalad7")) continue;
				if(ze.getName().startsWith("com/bawnorton")) continue;
				if(ze.getName().startsWith("io/github/classgraph")) continue;
				if(ze.getName().startsWith("it/unimi")) continue;
				if(ze.getName().startsWith("kotlin")) continue;

				//Manual error avoidance/compat
				for(Map.Entry<String, String> entry : manualOverrides.entrySet()) {
					if(ze.getName().startsWith(entry.getKey())){ //startsWith should be faster than contains
						String modid = manualOverrides.remove(entry.getKey());
						presentMods.put(modid, new ModInfo(modid, null, null));
						return;
					}
				}
			}
		}
		catch(Exception ex) {
			LOGGER.log(Level.ERROR, "FermiumJarScanner failed to search jar file {}.", modFile.getName());
		}
	}
	
	private static void searchModInfoRecursive(JsonElement element) {
		if(element instanceof JsonObject) {
			if(((JsonObject)element).has("modid")) {
				JsonObject obj = (JsonObject)element;
				String modid = obj.get("modid").getAsString().toLowerCase();
				String version = obj.has("version") ? obj.get("version").getAsString() : null;
				String name = obj.has("name") ? obj.get("name").getAsString() : null;
				presentMods.put(modid, new ModInfo(modid, version, name));
			}
			else {
				for(Map.Entry<String, JsonElement> entry : ((JsonObject)element).entrySet()) {
					searchModInfoRecursive(entry.getValue());
				}
			}
		}
		else if(element instanceof JsonArray) {
			for(JsonElement elem : ((JsonArray)element)) {
				searchModInfoRecursive(elem);
			}
		}
	}

	private static void parseMod(ClassInfo classInfo) {
		AnnotationParameterValueList params = classInfo.getAnnotationInfo().get(modClassName).getParameterValues();
		String modid = getOptionalAnnoParam(params.get("modid"));
		String version = getOptionalAnnoParam(params.get("version"));
		String name = getOptionalAnnoParam(params.get("name"));
		if(modid != null && !modid.isEmpty())
			presentMods.put(modid, new ModInfo(modid, version, name));
	}

	private static void parseMixinConfig(ClassInfo classInfo) {
		String mixinConfigName = (String) classInfo.getAnnotationInfo().get(MixinConfig.class.getName()).getParameterValues().get("name").getValue();
		if(mixinConfigName == null || mixinConfigName.isEmpty()) return;

		for(FieldInfo parsedField : classInfo.getFieldInfo()) {
			if(!parsedField.hasAnnotation(MixinConfig.MixinToggle.class.getName())) continue;

			try {
				String fieldName = parsedField.getName();
				AnnotationParameterValueList mixinToggleParams = parsedField.getAnnotationInfo().get(MixinConfig.MixinToggle.class.getName()).getParameterValues();
				boolean defaultValue = mixinToggleParams.get("defaultValue").getValue().toString().equals("true");
				String early = getOptionalAnnoParam(mixinToggleParams.get("earlyMixin"));
				String late = getOptionalAnnoParam(mixinToggleParams.get("lateMixin"));

				MixinConfigInfo configInfo = new MixinConfigInfo(mixinConfigName, fieldName, early, late, defaultValue);

				if (parsedField.hasAnnotation(MixinConfig.CompatHandling.class)) {
					for (AnnotationInfo compatAnno : parsedField.getAnnotationInfoRepeatable(MixinConfig.CompatHandling.class.getName())) {
						AnnotationParameterValueList compatParams = compatAnno.getParameterValues();
						String modId = getOptionalAnnoParam(compatParams.get("modid"));
						String modName = getOptionalAnnoParam(compatParams.get("modName"));
						String modVersionRange = getOptionalAnnoParam(compatParams.get("acceptableVersionRange"));
						boolean desired = (boolean) compatParams.get("desired").getValue();
						boolean disableMixin = getOptionalAnnoParamBoolean(compatParams.get("disableMixin"), true);
						boolean warnIngame = getOptionalAnnoParamBoolean(compatParams.get("warnIngame"), true);
						String reason = getOptionalAnnoParam(compatParams.get("reason"));
						configInfo.compatInfos.add(new MixinConfigInfo.CompatInfo(modId, modName, modVersionRange, desired, disableMixin, warnIngame, reason));
					}
				}
				mixinToggles.add(configInfo);
			} catch (Exception e) {
				LOGGER.error("Failed to parse @MixinToggle annotated field {} of {}, skipping", parsedField.getName(), classInfo.getName());
			}
		}
	}

	private static String getOptionalAnnoParam(AnnotationParameterValue param) {
		return param != null ? param.getValue().toString() : null;
	}

	private static boolean getOptionalAnnoParamBoolean(AnnotationParameterValue param, boolean defaultVal) {
		return param != null ? (boolean) param.getValue() : defaultVal;
	}

	private static void applyMixinConfig(MixinConfigInfo mixinConfig) {
		boolean shouldApply = getRawBooleanConfigValue(mixinConfig.modId, mixinConfig.name, mixinConfig.defaultValue);
		if(!shouldApply) return;

		if(!skipCompatHandlingChecks()) {
			for(MixinConfigInfo.CompatInfo compatAnno : mixinConfig.compatInfos) {
				//TODO: mod name + version range
				if(compatAnno.desired != isModPresent(compatAnno.modId)) {
					if(compatAnno.warnIngame) warningCount++;
					if(compatAnno.disableMixin) {
						shouldApply = false;
						LOGGER.log(Level.ERROR, "FermiumMixinConfig config \"{}\" from {} disabled as incompatible {} {}: {}.", mixinConfig.name, mixinConfig.modId, (
								compatAnno.desired ? "without" : "with"), compatAnno.modId, compatAnno.reason);
					}
					else {
						LOGGER.log(Level.WARN, "FermiumMixinConfig config \"{}\" from {} may have issues {} {}: {}.", mixinConfig.name, mixinConfig.modId, (
								compatAnno.desired ? "without" : "with"), compatAnno.modId, compatAnno.reason);
					}
				}
			}
		}

		if(shouldApply) {
			LOGGER.log(Level.INFO, "FermiumMixinConfig enqueueing mixin(s) parsed from annotated config {} from {}.", mixinConfig.name, mixinConfig.modId);
			//Don't need to provide suppliers as enqueue will just be skipped in the first place instead
			if(mixinConfig.earlyJson != null && !mixinConfig.earlyJson.isEmpty())
				FermiumRegistryAPI.enqueueMixin(false, mixinConfig.earlyJson);
			if(mixinConfig.lateJson != null && !mixinConfig.lateJson.isEmpty())
				FermiumRegistryAPI.enqueueMixin(true, mixinConfig.lateJson);
		}
	}
	
	private static Boolean skipCompatHandlingChecks = null;
	
	private static boolean skipCompatHandlingChecks() {
		if(skipCompatHandlingChecks == null) {
			skipCompatHandlingChecks = FermiumPlugin.CONFIG.get("general", "Override Mixin Config Compatibility Checks", FermiumBooterConfig.overrideMixinCompatibilityChecks).getBoolean();
			if(skipCompatHandlingChecks)
				LOGGER.log(Level.WARN, "FermiumMixinConfig detected Override Mixin Config Compatibility Checks as enabled, good luck, don't report issues.");
		}
		return skipCompatHandlingChecks;
	}
	
	private static final Map<String, String> modConfigMap = new HashMap<>();
	
	//Janky but works, has worked, will continue to work (hopefully), too lazy to change
	private static boolean getRawBooleanConfigValue(String modConfigName, String configFieldName, boolean defaultValue) {
		if(!modConfigMap.containsKey(modConfigName)) {
			//Read config file
			File configFile = new File("config", modConfigName + ".cfg");
			String rawConfigString = null;
			if(configFile.exists() && configFile.isFile()) {
				try(Stream<String> stream = Files.lines(configFile.toPath())) {
					//Only collect boolean config options
					rawConfigString = stream.filter(s -> s.trim().startsWith("B:")).collect(Collectors.joining());
				}
				catch(Exception ex) {
					LOGGER.log(Level.ERROR, "FermiumMixinConfig failed to read config {}.cfg: {}.", modConfigName, ex);
					rawConfigString = null;
				}
			}
			if(rawConfigString == null) LOGGER.log(Level.WARN, "FermiumMixinConfig config {}.cfg missing or failed to read, using default values.", modConfigName);
			modConfigMap.put(modConfigName, rawConfigString);
		}
		
		String modConfigString = modConfigMap.get(modConfigName);
		//File doesn't exist or failed to read, assume all default values
		if(modConfigString == null) return defaultValue;
		if(modConfigString.contains("B:\"" + configFieldName + "\"=")) {
			return modConfigString.contains("B:\"" + configFieldName + "\"=true");
		}
		//Option likely new and not written to file yet, return default
		return defaultValue;
	}
}