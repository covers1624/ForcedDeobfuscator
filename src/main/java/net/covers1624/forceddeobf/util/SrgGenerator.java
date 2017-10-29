/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.util;

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import joptsimple.internal.Strings;
import net.covers1624.forceddeobf.launch.FMLTweakWrapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.covers1624.forceddeobf.launch.MappingsManager.*;

/**
 * Cleaner implementation of: https://github.com/MinecraftForge/ForgeGradle/blob/FG_2.3/src/main/java/net/minecraftforge/gradle/tasks/GenSrgs.java
 * Created by covers1624 on 23/10/2017.
 */
public class SrgGenerator {

    private static final Logger logger = LogManager.getLogger("ForcedDeobfuscator");

    private static HashMap<String, String> fields = new HashMap<>();
    private static HashMap<String, String> methods = new HashMap<>();

    private static BiMap<String, String> srgClassMap = HashBiMap.create();
    private static BiMap<String, String> srgFieldMap = HashBiMap.create();
    private static BiMap<String, String> srgPackageMap = HashBiMap.create();
    private static BiMap<Pair<String, String>, Pair<String, String>> srgMethodMap = HashBiMap.create();
    private static List<String> excLines;
    private static Set<String> staticMethods;

    public static void generateSrgs(File srgZip) {
        logger.info("Starting FG Srg and Exc generation.");
        long start = System.nanoTime();

        logger.info("Reading fields..");
        Utils.readCsv(FIELDS_CSV, line -> fields.put(line[0], line[1]));
        logger.info("Reading methods..");
        Utils.readCsv(METHODS_CSV, line -> methods.put(line[0], line[1]));
        logger.info("Reading srgs..");
        Utils.processZipFile(srgZip, e -> e.getName().endsWith("joined.srg"), (f, e) -> parseSrg(Utils.newReader(f, e)));
        logger.info("Reading excs..");
        Utils.processZipFile(srgZip, e -> e.getName().endsWith("joined.exc"), (f, e) -> excLines = IOUtils.readLines(Utils.newReader(f, e)));
        logger.info("Reading statics..");
        Utils.processZipFile(srgZip, e -> e.getName().endsWith("static_methods.txt"), (f, e) -> staticMethods = Sets.newHashSet(IOUtils.readLines(Utils.newReader(f, e))));
        writeSrgs();
        writeExcs();
        long end = System.nanoTime();
        long delta = end - start;
        long ms = TimeUnit.NANOSECONDS.toMillis(delta);
        long s = TimeUnit.NANOSECONDS.toSeconds(delta);
        logger.info("Finished generation in {}s({}ms)", s, ms);
        try {
            System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-srg", SRG_NOTCH_SRG.getCanonicalPath());
            System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-mcp", SRG_NOTCH_MCP.getCanonicalPath());
            if (FMLTweakWrapper.obfEnvironment) {//Spoof the gradle arg so forge loads notch -> srg as usual.
                System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", SRG_NOTCH_SRG.getCanonicalPath());
            } else {
                System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", SRG_SRG_MCP.getCanonicalPath());
            }
            System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-srg", SRG_MCP_SRG.getCanonicalPath());
            System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-notch", SRG_MCP_NOTCH.getCanonicalPath());
            System.setProperty("net.minecraftforge.gradle.GradleStart.csvDir", CSV_DIR.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException("Unable to set environment variables for ForgeGradle", e);
        }

    }

    private static void parseSrg(Reader srg) {
        try {
            int ln = 0;
            for (String line : IOUtils.readLines(srg)) {
                if (!Strings.isNullOrEmpty(line) && !line.startsWith("#")) {
                    String type = line.substring(0, 2);
                    String[] args = line.substring(4).split(" ");

                    switch (type) {
                        case "PK":
                            srgPackageMap.put(args[0], args[1]);
                            break;
                        case "CL":
                            srgClassMap.put(args[0], args[1]);
                            break;
                        case "FD":
                            srgFieldMap.put(args[0], args[1]);
                            break;
                        case "MD":
                            srgMethodMap.put(Pair.of(args[0], args[1]), Pair.of(args[2], args[3]));
                            break;
                        default:
                            logger.warn("malformed SRG line@{}:{}", ln, line);
                            break;
                    }
                }
                ln++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Uanble to read SRG file", e);
        }
    }

    private static void writeSrgs() {
        logger.info("Generating Srgs..");
        try {
            PrintWriter notchToSrg = Utils.newPrintWriter(Utils.tryCreateFile(SRG_NOTCH_SRG));
            PrintWriter notchToMcp = Utils.newPrintWriter(Utils.tryCreateFile(SRG_NOTCH_MCP));
            PrintWriter srgToMcp = Utils.newPrintWriter(Utils.tryCreateFile(SRG_SRG_MCP));
            PrintWriter mcpToSrg = Utils.newPrintWriter(Utils.tryCreateFile(SRG_MCP_SRG));
            PrintWriter mcpToNotch = Utils.newPrintWriter(Utils.tryCreateFile(SRG_MCP_NOTCH));

            logger.info("Writing packages..");
            srgPackageMap.forEach((key, value) -> {
                notchToSrg.println(String.format("PK: %s %s", key, value));
                notchToMcp.println(String.format("PK: %s %s", key, value));
                mcpToNotch.println(String.format("PK: %s %s", value, key));
            });
            logger.info("Writing classes..");
            srgClassMap.forEach((key, value) -> {
                notchToSrg.println(String.format("CL: %s %s", key, value));
                notchToMcp.println(String.format("CL: %s %s", key, value));
                srgToMcp.println(String.format("CL: %s %s", value, value));
                mcpToSrg.println(String.format("CL: %s %s", value, value));
                mcpToNotch.println(String.format("CL: %s %s", value, key));
            });
            logger.info("Writing fields..");
            srgFieldMap.forEach((key, value) -> {
                notchToSrg.println(String.format("FD: %s %s", key, value));
                String tmp = value.substring(value.lastIndexOf('/') + 1);
                String mcp = fields.containsKey(tmp) ? value.replace(tmp, fields.get(tmp)) : value;
                notchToMcp.println(String.format("FD: %s %s", key, mcp));
                srgToMcp.println(String.format("FD: %s %s", value, mcp));
                mcpToSrg.println(String.format("FD: %s %s", mcp, value));
                mcpToNotch.println(String.format("FD: %s %s", mcp, key));
            });
            logger.info("Writing methods..");
            srgMethodMap.forEach((k, v) -> {
                String key = k.getLeft() + " " + k.getRight();
                String value = v.getLeft() + " " + v.getRight();
                notchToSrg.println(String.format("MD: %s %s", key, value));
                String tmp = v.getLeft().substring(v.getLeft().lastIndexOf('/') + 1);
                String mcp = methods.containsKey(tmp) ? value.replace(tmp, methods.get(tmp)) : value;
                notchToMcp.println(String.format("MD: %s %s", key, mcp));
                srgToMcp.println(String.format("MD: %s %s", value, mcp));
                mcpToSrg.println(String.format("MD: %s %s", mcp, value));
                mcpToNotch.println(String.format("MD: %s %s", mcp, key));
            });
            IOUtils.closeQuietly(notchToSrg, notchToMcp, srgToMcp, mcpToSrg, mcpToNotch);
            logger.info("Done writing Srgs.");
        } catch (IOException e) {
            throw new RuntimeException("Unable to write out SRG files.", e);
        }
    }

    private static void writeExcs() {
        logger.info("Writing Excs..");
        try {
            PrintWriter srgOut = Utils.newPrintWriter(Utils.tryCreateFile(new File(SRG_DIR, "srg.exc")));
            PrintWriter mcpOut = Utils.newPrintWriter(Utils.tryCreateFile(new File(SRG_DIR, "mcp.exc")));

            Map<String, String> tmp = new HashMap<>();
            for (String line : excLines) {
                if (line.startsWith("#")) {
                    tmp.put(line, null);
                } else {
                    String[] split = line.split("=");
                    tmp.put(split[0], split[1]);
                }
            }

            Joiner comma = Joiner.on(',');
            srgMethodMap.values().forEach(p -> {
                String cls = p.getLeft().substring(0, p.getLeft().lastIndexOf('/'));
                String name = p.getLeft().substring(cls.length() + 1);
                if (!name.startsWith("func_")) {
                    return;
                }
                String prefix = "p_" + name.split("_")[1];
                List<String> args = new ArrayList<>();

                int idx = staticMethods.contains(name) ? 0 : 1;
                for (Type type : Type.getArgumentTypes(p.getRight())) {
                    args.add(prefix + "_" + idx++ + "_");
                    if (type == Type.DOUBLE_TYPE || type == Type.LONG_TYPE) {
                        idx++;
                    }
                }
                if (args.size() > 0) {
                    String key = cls + "." + name + p.getRight();
                    String info = tmp.get(key);
                    info = info != null ? info.substring(0, info.indexOf('|')) : "";
                    tmp.put(key, info + "|" + comma.join(args));
                }
            });

            List<String> excLines = new ArrayList<>();
            List<String> keys = Lists.newArrayList(tmp.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                String value = tmp.get(key);
                excLines.add(value != null ? key + "=" + value : key);
            }
            excLines.forEach(line -> {
                srgOut.println(line);

                String[] split = line.split("=");
                int sigIndex = split[0].indexOf('(');
                int dotIndex = split[0].indexOf('.');
                if (line.startsWith("#") || sigIndex == -1 || dotIndex == -1) {
                    mcpOut.println(line);
                    return;
                }

                String name = split[0].substring(dotIndex + 1, sigIndex);
                if (methods.containsKey(name)) {
                    name = methods.get(name);
                }
                mcpOut.println(split[0].substring(0, dotIndex) + "." + name + split[0].substring(sigIndex) + "=" + split[1]);
            });
            logger.info("Done writing Excs.");
            IOUtils.closeQuietly(srgOut, mcpOut);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write out Exc files.", e);
        }
    }

}
