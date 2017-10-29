/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.loading;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.covers1624.forceddeobf.launch.RemapTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.asm.ASMTransformerWrapper.TransformerWrapper;
import net.minecraftforge.fml.common.asm.transformers.AccessTransformer;
import net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer;
import net.minecraftforge.fml.common.asm.transformers.EventSubscriberTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Forcibly sorts the transformer list.
 * This is called from an injected hook.
 *
 * Created by covers1624 on 29/10/2017.
 */
public class TransformerSorter {

    private static Logger logger = LogManager.getLogger("ForcedDeobf-PreLaunch");
    public static final Field f_transformers;
    public static final Field f_parent;
    public static final Field f_modifiers;
    public static final Field f_Modifier_name;
    public static final Field f_Modifier_desc;
    public static final Field f_Modifier_targetAccess;
    public static final Field f_Modifier_changeFinal;
    public static final Field f_Modifier_markFinal;

    static {
        try {
            f_transformers = LaunchClassLoader.class.getDeclaredField("transformers");
            f_parent = TransformerWrapper.class.getDeclaredField("parent");
            f_modifiers = AccessTransformer.class.getDeclaredField("modifiers");
            Class<?> c_Modifier = Class.forName(AccessTransformer.class.getName() + "$Modifier", false, Launch.classLoader);
            f_Modifier_name = c_Modifier.getDeclaredField("name");
            f_Modifier_desc = c_Modifier.getDeclaredField("desc");
            f_Modifier_targetAccess = c_Modifier.getDeclaredField("targetAccess");
            f_Modifier_changeFinal = c_Modifier.getDeclaredField("changeFinal");
            f_Modifier_markFinal = c_Modifier.getDeclaredField("markFinal");

            setAccessible(f_transformers, f_parent, f_modifiers, f_Modifier_name, f_Modifier_desc);
            setAccessible(f_Modifier_targetAccess, f_Modifier_changeFinal, f_Modifier_markFinal);
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException("Unable to load fields.", e);
        }
    }

    @SuppressWarnings ("unchecked")
    public static void doStuff() {
        LaunchClassLoader classLoader = Launch.classLoader;
        Joiner joiner = Joiner.on("\n    ");
        try {
            int remapIndex = -1;
            int subscriberIndex = -1;
            int deobfTransformer = -1;
            List<IClassTransformer> transformers = getTransformers(Launch.classLoader);
            logger.info("Searching transformer list..\n    {}", joiner.join(transformers));
            for (int i = 0; i < transformers.size(); i++) {
                IClassTransformer transformer = transformers.get(i);
                if (transformer instanceof RemapTransformer) {
                    if (remapIndex != -1) {
                        throw new RuntimeException("What? There is more than 1 RemapTransformer..");
                    }
                    logger.info("Found RemapTransformer at index {}.", i);
                    remapIndex = i;
                    continue;
                }
                if (transformer instanceof TransformerWrapper) {
                    IClassTransformer wrapped = (IClassTransformer) f_parent.get(transformer);
                    if (wrapped instanceof EventSubscriberTransformer) {
                        if (subscriberIndex != -1) {
                            throw new RuntimeException("What? There is more than 1 EventSubscriberTransformer..");
                        }
                        logger.info("Found EventSubscriberTransformer at index {}.", i);
                        subscriberIndex = i;
                        continue;
                    }
                }
                if (transformer instanceof DeobfuscationTransformer) {
                    if (deobfTransformer != -1) {
                        throw new RuntimeException("What? There is more than 1 DeobfuscationTransformer..");
                    }
                    logger.info("Found DeobfuscationTransformer at index {}.", i);
                    deobfTransformer = i;
                }
            }
            if (remapIndex == -1) {
                throw new RuntimeException("Unable to find RemapTransformer.");
            }
            if (subscriberIndex == -1) {
                throw new RuntimeException("Unable to find EventSubscriberTransformer.");
            }
            if (deobfTransformer == -1) {
                throw new RuntimeException("Unable to find DeobfuscationTransformer.");
            }
            logger.info("Found Transformers. Moving..");
            IClassTransformer remapper = transformers.get(remapIndex);
            IClassTransformer deobf = transformers.get(deobfTransformer);
            transformers.remove(remapper);//Remove both.
            transformers.remove(deobf);
            transformers.add(subscriberIndex, remapper);//Add ours after the EventSubscriberTransformer.
            transformers.add(subscriberIndex, deobf);//Add back the deobf transformer, makes it push ours after it.
            logger.info("Transformers moved. Result: \n    {}", joiner.join(transformers));
        } catch (Exception e) {
            throw new RuntimeException("Unable to move RemapTransformer's index in the transformer list.", e);
        }
        try {
            Set<String> acceptableCasualties = new HashSet<>();
            acceptableCasualties.add("<init>");
            List<IClassTransformer> transformers = getTransformers(Launch.classLoader);
            logger.info("Remapping AccessTransformers..");
            int mapped = 0;
            List<String> failedMappings = new ArrayList<>();
            ATRemapper remapper = new ATRemapper();
            for (IClassTransformer transformer : transformers) {
                if (transformer instanceof AccessTransformer) {//If its an AT class.
                    Multimap<String, ?> modifiers = get(f_modifiers, transformer, Multimap.class);//Grab the modifiers list.
                    Multimap<String, Object> t_modifiers = HashMultimap.create();
                    for (Entry<String, ?> entry : modifiers.entries()) {
                        Object mod = entry.getValue();

                        String m_owner = entry.getKey().replace(".", "/");//Grad the fields.
                        String m_name = get(f_Modifier_name, mod, String.class);
                        String m_desc = get(f_Modifier_desc, mod, String.class);

                        boolean isMethod = m_desc.contains("(");
                        boolean isClass = m_name.length() == 0;
                        boolean isAll = m_name.equals("*");

                        if (!isAll) {//If the name doesnt have a '*'
                            if (isMethod) {//Remap method.
                                m_name = remapper.mapMethodName(m_owner, m_name, m_desc);
                            } else if (!isClass) {//Remap field.
                                m_name = remapper.mapFieldName(m_owner, m_name, m_desc);
                            }
                        }

                        //Always remap class name.
                        m_owner = remapper.mapType(m_owner);

                        if (!isAll) {//If the name doesnt have a '*'
                            if (isMethod) {//Remap method desc.
                                m_desc = remapper.mapMethodDesc(m_desc);
                            } else if (m_desc.length() > 0) {//Remap field desc
                                m_desc = remapper.mapDesc(m_desc);
                            }
                        }
                        if (!hasChanged(mod, entry.getKey().replace(".", "/"), m_owner, m_name, m_desc) && !isAll && !isClass && !acceptableCasualties.contains(m_name)) {
                            failedMappings.add(buildATLine(mod, m_owner, m_name, m_desc));
                        }
                        //Set them back.
                        set(f_Modifier_name, mod, m_name);
                        set(f_Modifier_desc, mod, m_desc);
                        //Add to new list.
                        mapped++;
                        t_modifiers.put(m_owner.replace("/", "."), mod);
                    }
                    set(f_modifiers, transformer, t_modifiers);

                }
            }
            logger.info("Finished remapping AccessTransformers with a total of {} mapped entries.", mapped);
            if (!failedMappings.isEmpty()) {
                logger.warn("ForceDeobfuscator was unable to remap {} AccessTransformer entries!\n    {}", failedMappings.size(), joiner.join(failedMappings));
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to remap AccessTransformers.", e);
        }
        //throw new RuntimeException("HALT, WHO GOES THERE!");
    }

    @SuppressWarnings ("unchecked")
    public static List<IClassTransformer> getTransformers(LaunchClassLoader classLoader) {
        try {
            return (List<IClassTransformer>) f_transformers.get(classLoader);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Unable to reflectivly grab the Transformer list from LaunchClassLoader", e);
        }
    }

    public static void setAccessible(AccessibleObject... objects) {
        if (objects != null) {
            for (AccessibleObject object : objects) {
                object.setAccessible(true);
            }
        }
    }

    @SuppressWarnings ("unchecked")
    public static <R> R get(Field f, Object instance, Class<R> ret) {
        try {
            return (R) f.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to get field value.", e);
        }
    }

    public static void set(Field f, Object instance, Object value) {
        try {
            f.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to set field value.", e);
        }
    }

    public static boolean hasChanged(Object mod, String oldOwner, String newOwner, String newName, String newDesc) {
        String oldName = get(f_Modifier_name, mod, String.class);
        String oldDesc = get(f_Modifier_desc, mod, String.class);
        return !oldOwner.equals(newOwner) || !oldName.equals(newName) || !oldDesc.equals(newDesc);

    }

    public static String buildATLine(Object mod, String owner, String name, String desc) {
        int targetAccess = get(f_Modifier_targetAccess, mod, Integer.class);
        boolean changeFinal = get(f_Modifier_changeFinal, mod, Boolean.class);
        boolean markFinal = get(f_Modifier_markFinal, mod, Boolean.class);
        StringBuilder builder = new StringBuilder();
        switch (targetAccess) {
            case Opcodes.ACC_PUBLIC:
                builder.append("public");
                break;
            case Opcodes.ACC_PRIVATE:
                builder.append("private");
                break;
            case Opcodes.ACC_PROTECTED:
                builder.append("protected");
                break;
            default:
                builder.append("UNKNOWN_ACC(0x");
                builder.append(Long.toString((long) targetAccess << 32 >>> 32, 16).toUpperCase());
                builder.append(")");
        }
        if (changeFinal) {
            builder.append(markFinal ? "+f" : "-f");
        }
        builder.append(" ");
        builder.append(owner.replace("/", "."));
        builder.append(" ");
        builder.append(name);
        builder.append(desc);
        return builder.toString();
    }

    public static class ATRemapper extends Remapper {

        private static final Remapper forgeRemapper = FMLDeobfuscatingRemapper.INSTANCE;
        private static final Remapper srgRemapper = RemapTransformer.MCPRemapper.INSTANCE;

        @Override
        public String mapType(String type) {
            return forgeRemapper.mapType(type);
        }

        @Override
        public String mapDesc(String desc) {
            return forgeRemapper.mapDesc(desc);
        }

        @Override
        public String mapMethodDesc(String desc) {
            return forgeRemapper.mapMethodDesc(desc);
        }

        @Override
        public String mapMethodName(String owner, String name, String desc) {
            //Ask forge first, as the AT may be in Obf names.
            String ret = forgeRemapper.mapMethodName(owner, name, desc);
            if (ret.equals(name)) {
                //No? k, i got dis bruh.
                ret = srgRemapper.mapMethodName(owner, name, desc);
            }
            return ret;
        }

        @Override
        public String mapFieldName(String owner, String name, String desc) {
            //Ask forge first, as the AT may be in Obf names.
            String ret = forgeRemapper.mapFieldName(owner, name, desc);
            if (ret.equals(name)) {
                //No? k, i got dis bruh.
                ret = srgRemapper.mapFieldName(owner, name, desc);
            }
            return ret;
        }
    }
}
