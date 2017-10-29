/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.launch;

import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.covers1624.forceddeobf.util.Singleton;
import net.covers1624.forceddeobf.util.SrgGenerator;
import net.covers1624.forceddeobf.util.Utils;
import net.covers1624.forceddeobf.util.WindowCloseListener;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.covers1624.forceddeobf.launch.FMLTweakWrapper.MC_VERSION;

/**
 * The beating heart of ForcedDeobfuscator.
 * This class manages downloading, caching, extracting and generating the various SRG files ForgeGradle usually does.
 * Its really quite simple and probably very horrible.
 *
 * Created by covers1624 on 21/10/2017.
 */
public class MappingsManager {

    private static final Logger logger = LogManager.getLogger("ForcedDeobfuscator");
    private static Gson gson = new Gson();

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";
    private static final String FORGE_MAVEN = "http://files.minecraftforge.net/maven";
    private static final String MCP_JSON = FORGE_MAVEN + "/de/oceanlabs/mcp/versions.json";
    //private static final String MCP_JSON = "http://export.mcpbot.bspk.rs/versions.json";//Alternate.
    public static final File MAPPINGS_FOLDER = new File("forced_deobfuscator");
    private static final File CACHE_FOLDER = new File(MAPPINGS_FOLDER, "cache");
    private static final File LAST_SELECTION = new File(MAPPINGS_FOLDER, "last_selection.txt");

    //@formatter:off
    public static final File CSV_DIR =       new File(MAPPINGS_FOLDER, "curr");
    public static final File METHODS_CSV =   new File(CSV_DIR, "methods.csv");
    public static final File FIELDS_CSV =    new File(CSV_DIR, "fields.csv");
    public static final File PARAMS_CSV =    new File(CSV_DIR, "params.csv");
    public static final File SRG_DIR =       new File(CSV_DIR, "srgs");
    public static final File SRG_NOTCH_SRG = new File(SRG_DIR, "notch-srg.srg");
    public static final File SRG_NOTCH_MCP = new File(SRG_DIR, "notch-mcp.srg");
    public static final File SRG_SRG_MCP =   new File(SRG_DIR, "srg-mcp.srg");
    public static final File SRG_MCP_SRG =   new File(SRG_DIR, "mcp-srg.srg");
    public static final File SRG_MCP_NOTCH = new File(SRG_DIR, "mcp-notch.srg");
    //@formatter:on

    public static void init() {
        Utils.delete(CSV_DIR);//Remove the dir, we need to start from scratch every time.
        Map<String, List<String>> snapshotVersions = new HashMap<>();
        Map<String, List<String>> stableVersions = new HashMap<>();

        String v = fetchVersions();//Grab and parse all MCP versions in existence.
        JsonObject object = gson.fromJson(v, JsonObject.class);
        for (Entry<String, JsonElement> element : object.entrySet()) {
            String version = element.getKey();
            JsonObject vObject = element.getValue().getAsJsonObject();
            {
                List<String> versions = snapshotVersions.computeIfAbsent(version, key -> new ArrayList<>());
                JsonArray array = vObject.getAsJsonArray("snapshot");
                array.forEach(jsonElement -> versions.add(jsonElement.getAsJsonPrimitive().getAsString()));
            }
            {
                List<String> versions = stableVersions.computeIfAbsent(version, key -> new ArrayList<>());
                JsonArray array = vObject.getAsJsonArray("stable");
                array.forEach(jsonElement -> versions.add(jsonElement.getAsJsonPrimitive().getAsString()));
            }
        }
        //Grab a list of compatible mappings for this mc version.
        //For example, Mc version 1.12.2 will allow us to use mappings for 1.12.1 and 1.12.
        List<String> compatibleMappings = getCompatibleMappings(snapshotVersions, stableVersions);
        String prevSelection = Utils.readFirstLine(LAST_SELECTION);//Read the last selection.
        //Ask for the last version and check if it is valid.
        String selectedMappings = askMappings(compatibleMappings, prevSelection, s -> {
            try {
                String[] split = s.split("_");
                Collection<List<String>> validValues = split[0].equals("stable") ? stableVersions.values() : snapshotVersions.values();
                Set<String> all = validValues.stream().flatMap(Collection::stream).collect(Collectors.toSet());
                return all.contains(split[1]);
            } catch (Throwable e) {
                return false;
            }
        });
        //Well this probably isn't meant to be null or empty, The GUI probably asked to exit.
        if (Strings.isNullOrEmpty(selectedMappings)) {
            throw new RuntimeException("Exit");
        }
        //If the selection has changed since last time update our file.
        if (!selectedMappings.equals(prevSelection)) {
            Utils.setFirstLine(LAST_SELECTION, selectedMappings);
        }
        logger.info("Selected mappings: {}", selectedMappings);
        String mappingMCVersion = null;
        String channel;
        String version;
        {//Grab the mc version these mappings are designed for.
            String[] split = selectedMappings.split("_");
            channel = split[0];
            version = split[1];
            Map<String, List<String>> toSearch = split[0].equals("stable") ? stableVersions : snapshotVersions;
            for (Entry<String, List<String>> entry : toSearch.entrySet()) {
                for (String m : entry.getValue()) {
                    if (m.equals(version)) {
                        mappingMCVersion = entry.getKey();
                    }
                }
            }
        }
        String artifactName = "mcp_" + channel;
        String artifactVersion = version + "-" + mappingMCVersion;
        String artifact = String.format("de.oceanlabs.mcp:%s:%s", artifactName, artifactVersion);

        //Download the stuffs.
        File mappingsZip = downloadArtifact(FORGE_MAVEN, artifact, ".zip", CACHE_FOLDER);
        File srgZip = downloadArtifact(FORGE_MAVEN, String.format("de.oceanlabs.mcp:mcp:%s:srg", MC_VERSION), ".zip", CACHE_FOLDER);
        Utils.unzip(mappingsZip, CSV_DIR);//Unzip the CSV's
        SrgGenerator.generateSrgs(srgZip);//Generate the many SRG files.
    }

    private static String askMappings(List<String> options, String last, Predicate<String> validator) {
        MappingsGui mappingsGui = new MappingsGui();
        mappingsGui.comboBox.setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
        if (!Strings.isNullOrEmpty(last)) {//Set the comboBox selection or the text box based on the last selection.
            if (options.contains(last)) {
                mappingsGui.comboBox.setSelectedItem(last);
            } else {
                mappingsGui.textField.setText(last);
            }
        }
        //Singleton instances to get around final fields and lambdas.
        Singleton<Boolean> shouldClose = new Singleton<>(false);
        Singleton<String> selected = new Singleton<>(null);
        mappingsGui.addWindowListener(new WindowCloseListener(e -> shouldClose.set(true)));
        mappingsGui.okButton.addActionListener(e -> {
            mappingsGui.setVisible(false);//Hide the window and grab the mappings.
            String sel;
            if (!Strings.isNullOrEmpty(mappingsGui.textField.getText())) {
                sel = mappingsGui.textField.getText();
            } else {
                sel = (String) mappingsGui.comboBox.getSelectedItem();
            }
            selected.set(sel);
        });

        while (true) {//Do this forever
            mappingsGui.setVisible(true);//Make it visible.
            if (shouldClose.get()) {
                break;//Should we close?
            }
            if (!validator.test(selected.get())) {//Test against the validator, for.. Validation..
                JOptionPane.showMessageDialog(null, "Invalid Mappings " + selected.get());//Nope, try again.
            } else {
                break;//Sweet, break out.
            }
        }
        mappingsGui.dispose();//Dispose.
        if (shouldClose.get()) {//Should we close?
            throw new RuntimeException("Stop.");
        }
        return selected.get();
    }

    private static String fetchVersions() {
        try {
            return IOUtils.toString(openURL(MCP_JSON), Charset.defaultCharset());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getCompatibleMappings(Map<String, List<String>> snapshotVersions, Map<String, List<String>> stableVersions) {
        List<String> mappings = new LinkedList<>();
        List<String> similarVersions = getSimilarMcVersions();
        {
            List<String> stables = new ArrayList<>();
            for (String v : similarVersions) {
                if (stableVersions.containsKey(v)) {
                    stables.addAll(stableVersions.get(v));
                }
            }
            stables.sort(Comparator.comparing(Integer::valueOf, (i, j) -> Integer.compare(j, i)));
            stables.forEach(e -> mappings.add("stable_" + e));
        }
        {
            List<String> snapshots = new ArrayList<>();
            for (String v : similarVersions) {
                if (snapshotVersions.containsKey(v)) {
                    snapshots.addAll(snapshotVersions.get(v));
                }
            }
            snapshots.sort(Comparator.comparing(Integer::valueOf, (i, j) -> Integer.compare(j, i)));
            snapshots.forEach(e -> mappings.add("snapshot_" + e));
        }
        return mappings;
    }

    private static List<String> getSimilarMcVersions() {
        String mcVersion = MC_VERSION;
        String minorMc = Utils.countChar(mcVersion, '.') >= 2 ? mcVersion.substring(0, mcVersion.lastIndexOf(".")) : mcVersion;
        List<String> versions = new ArrayList<>();

        versions.add(minorMc);
        if (!mcVersion.equals(minorMc)) {
            int max = Integer.parseInt(mcVersion.substring(mcVersion.lastIndexOf('.') + 1));
            int start = 1;
            for (int i = start; i <= max; i++) {
                versions.add(minorMc + "." + i);
            }
        }
        return versions;
    }

    /**
     * Converts a maven artifact to a URL. Quite basic, probably broken somehow.
     * E.G:
     * <pre>
     * - Repo: "files.minecraftforge.net/maven"
     * - Artifact1: "de.oceanlabs.mcp:mcp_snapshot:20171018-1.12"
     * - Artifact2: "de.oceanlabs.mcp:mcp:1.12.2:srg"
     * - Output:
     *         http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/20171018-1.12/mcp_snapshot-20171018-1.12.zip
     *         http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/1.12.2/mcp-1.12.2-srg.zip
     * </pre>
     *
     * @param repo     The repo to prefix the artifact with.
     * @param artifact The artifact we are converting.
     * @param ext      The extension to add at the end of the artifact.
     * @return The URL for the artifact.
     */
    public static URL urlifyMaven(String repo, String artifact, String ext) {
        try {
            if (!repo.endsWith("/")) {
                repo += "/";
            }
            String[] segs = artifact.split(":");
            String base = segs[0].replace(".", "/") + "/" + segs[1] + "/" + segs[2] + "/";
            for (int i = 1; i < segs.length; i++) {
                if (i != 1) {
                    base += "-";
                }
                base += segs[i];
            }
            return new URL(repo + base + ext);

        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream openURL(String url) throws IOException {
        return openURL(new URL(url));
    }

    public static InputStream openURL(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("User-Agent", USER_AGENT);
        return connection.getInputStream();
    }

    /**
     * Downloads a maven artifact and returns the file on disk.
     *
     * @param repo     The repo to download the file from.
     * @param artifact The artifact to download.
     * @param ext      The extension of the artifact.
     * @param cacheDir The directory to store the artifact.
     * @return The file on disk.
     */
    public static File downloadArtifact(String repo, String artifact, String ext, File cacheDir) {
        try {
            URL artifactURL = urlifyMaven(repo, artifact, ext);
            File artifactFile = new File(cacheDir, artifactURL.toString().replace(repo + "/", ""));

            if (artifactFile.exists()) {//File exists, k lets check its hash.
                HashCode expectedHash;
                HashFunction func = Hashing.sha1();
                URL hashURL = urlifyMaven(repo, artifact, ext + ".sha1");
                {
                    InputStream is = openURL(hashURL);//Grab remote expected hash.
                    expectedHash = HashCode.fromString(IOUtils.toString(is, Charset.defaultCharset()));
                    IOUtils.closeQuietly(is);
                }
                InputStream is = new FileInputStream(artifactFile);
                HashCode fileHash = func.hashBytes(IOUtils.toByteArray(is));
                IOUtils.closeQuietly(is);
                if (fileHash.equals(expectedHash)) {//Hash all good? sweet lets return the local file.
                    logger.info("Artifact validated: " + artifact);
                    return artifactFile;
                }
                logger.info("Artifact {} has invalid hash, Re-Downloading. Expected: {}, Got: {}", artifact, expectedHash, fileHash);
            }
            //K, hash is either bad or the file doesnt exist.
            logger.info("Downloading artifact {}{\"{}\"}, From: \"{}\"", artifact, artifactFile.getAbsolutePath(), artifactURL);
            FileOutputStream fos = new FileOutputStream(Utils.tryCreateFile(artifactFile));
            InputStream is = openURL(artifactURL);
            IOUtils.copy(is, fos);
            IOUtils.closeQuietly(is, fos);
            logger.info("Download finished.");
            return artifactFile;
        } catch (Exception e) {
            throw new RuntimeException("Unable to download artifact " + artifact, e);
        }
    }
}
