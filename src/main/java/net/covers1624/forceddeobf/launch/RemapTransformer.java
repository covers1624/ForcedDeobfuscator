/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.launch;

import com.google.common.base.Charsets;
import net.covers1624.forceddeobf.util.Utils;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remapper to remap srg -> MCP
 * Created by covers1624 on 28/10/2017.
 */
public class RemapTransformer implements IClassTransformer {

    private static final Logger logger = LogManager.getLogger("ForcedDeobfuscator");

    public RemapTransformer() {
        MCPRemapper.INSTANCE.load(MappingsManager.SRG_SRG_MCP);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassRemapper remapper = new ClassRemapper(writer, MCPRemapper.INSTANCE);
        reader.accept(remapper, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    //This remapper doesn't care about class hierarchy.
    //If its the name we want, transform it!
    public static class MCPRemapper extends Remapper {

        public static final MCPRemapper INSTANCE = new MCPRemapper();

        private Map<String, String> fieldMap = new HashMap<>();
        private Map<String, String> methodMap = new HashMap<>();

        public void load(File mappings) {
            try {
                List<String> lines = IOUtils.readLines(new FileInputStream(mappings), Charsets.UTF_8);
                lines.stream().filter(Utils::notComment).forEach(line -> {
                    String type = line.substring(0, 2);
                    String[] args = line.substring(4).split(" ");
                    switch (type) {
                        case "FD": {
                            String oldName = args[0].substring(args[0].lastIndexOf('/') + 1);
                            int lastSlash = args[1].lastIndexOf('/');
                            String newName = args[1].substring(lastSlash + 1);
                            fieldMap.put(oldName, newName);
                            break;
                        }
                        case "MD": {
                            String oldName = args[0].substring(args[0].lastIndexOf('/') + 1);
                            int lastSlash = args[2].lastIndexOf('/');
                            String newName = args[2].substring(lastSlash + 1);
                            methodMap.put(oldName, newName);
                            break;
                        }
                    }
                });
            } catch (IOException ioe) {
                throw new RuntimeException("Exception whilst loading Mappings.");
            }
        }

        @Override
        public String mapFieldName(String owner, String name, String desc) {
            if (fieldMap.containsKey(name)) {
                return fieldMap.get(name);
            }
            return name;
        }

        @Override
        public String mapMethodName(String owner, String name, String desc) {
            if (methodMap.containsKey(name)) {
                return methodMap.get(name);
            }
            return name;
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String desc) {
            if (methodMap.containsKey(name)) {
                return methodMap.get(name);
            }
            return super.mapInvokeDynamicMethodName(name, desc);
        }
    }
}
