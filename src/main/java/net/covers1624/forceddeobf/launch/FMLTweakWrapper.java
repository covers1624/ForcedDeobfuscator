/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.launch;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.launcher.FMLTweaker;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Allows us to run before FML and still Have FML as essentially the "main" tweaker.
 *
 * Created by covers1624 on 20/10/2017.
 */
public class FMLTweakWrapper implements ITweaker {

    private static Logger logger = LogManager.getLogger("ForcedDeobf-WrappedTweaker");
    private static final String POKE_CLASS = "net.minecraft.world.World";
    //If this environment is actually obfuscated.
    public static boolean obfEnvironment = false;

    public static String MC_VERSION;

    static {
        try {
            //I have no words for how retarded this is, this is needed so we can support multiple versions from the one jar.
            Field field = ForgeVersion.class.getDeclaredField("mcVersion");
            MC_VERSION = (String) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to reflectively retrieve current MC version from ForgeVersion.", e);
        }
    }

    private ITweaker tweaker;

    @SuppressWarnings ("unchecked")
    public FMLTweakWrapper() {
        LaunchClassLoader classLoader = Launch.classLoader;
        hackClassLoader(classLoader);//Hack the classloader, so FML "thinks" its a deobf environment.
        classLoader.addClassLoaderExclusion("net.minecraftforge.fml.common.launcher.FMLTweaker");//Add classloader exclusions.
        classLoader.addClassLoaderExclusion("net.covers1624.forceddeobf.launch.");
        classLoader.addClassLoaderExclusion("au.com.bytecode.opencsv.");
        MappingsManager.init();//Init the mappings manager.
        tweaker = new FMLTweaker();//Init FML.
        replaceSecurityManager();//Replace FML's security manager, its just noise and this is a dev tool.
    }

    public static void replaceSecurityManager() {
        try {
            logger.info("Attempting to remove FML's Security manager.");
            //Grab the native method used to get all fields before filtering.
            Method m = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
            m.setAccessible(true);
            Field[] fields = (Field[]) m.invoke(System.class, false);
            Field security = null;
            for (Field field : fields) {//Filter for our field.
                if (field.getName().equals("security")) {
                    security = field;
                }
            }
            security.setAccessible(true);
            security.set(null, null);//Bye Bye security manager. o/
            logger.info("Successfully removed the security manager.");
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Unable to override FML's Security manager.", e);
        }
    }

    @SuppressWarnings ("unchecked")
    private static void hackClassLoader(LaunchClassLoader classLoader) {
        try {
            byte[] bytes = classLoader.getClassBytes(POKE_CLASS);
            if (bytes == null) {
                Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
                logger.info("We are actually in an obf environment, attempting to make the classloader lie to FML.");
                obfEnvironment = true;
                Field field = LaunchClassLoader.class.getDeclaredField("resourceCache");
                field.setAccessible(true);
                Map<String, byte[]> resourceCache = (Map<String, byte[]>) field.get(classLoader);
                resourceCache.put(POKE_CLASS, new byte[0]);//Inject fake data. FML compares against null, not contents.
                classLoader.clearNegativeEntries(Collections.singleton(POKE_CLASS));//Remove the class form the "known" missing classes set.
                logger.info("Successfully hacked the classloader to lie to FML.");
            }

        } catch (IOException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to hack LaunchClassLoader cache.", e);
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        logger.log(Level.INFO, "Calling wrapped tweak class {}", FMLTweaker.class.getName());
        tweaker.acceptOptions(args, gameDir, assetsDir, profile);
    }

    @SuppressWarnings ("unchecked")
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        tweaker.injectIntoClassLoader(classLoader);
        //Register our remap transformer.
        classLoader.registerTransformer("net.covers1624.forceddeobf.launch.RemapTransformer");
    }

    @Override
    public String getLaunchTarget() {
        tweaker.getLaunchTarget();
        return "net.covers1624.forceddeobf.loading.LaunchBouncer";
    }

    @Override
    public String[] getLaunchArguments() {
        return tweaker.getLaunchArguments();
    }

    @SuppressWarnings ("unchecked")
    public static List<IClassTransformer> getTransformers(LaunchClassLoader classLoader) {
        try {
            Field field = classLoader.getClass().getDeclaredField("transformers");
            field.setAccessible(true);
            return (List<IClassTransformer>) field.get(classLoader);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Unable to reflectivly grab the Transformer list from LaunchClassLoader", e);
        }
    }
}
