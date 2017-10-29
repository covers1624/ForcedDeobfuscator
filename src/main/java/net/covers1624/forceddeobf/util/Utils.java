/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.util;

import au.com.bytecode.opencsv.CSVReader;
import com.google.common.base.Charsets;
import joptsimple.internal.Strings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Random utilities.
 * Feel free to ignore the license on these :D
 * Created by covers1624 on 23/10/2017.
 */
public class Utils {

    private static final Logger logger = LogManager.getLogger("ForcedDeobfuscator");

    /**
     * Reads the first line of a file.
     * If it errors, it deletes the file.
     *
     * @param file The file to read.
     * @return The read string.
     */
    public static String readFirstLine(File file) {
        String ret = null;
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                ret = IOUtils.toString(reader);
                IOUtils.closeQuietly(reader);
            } catch (IOException e) {
                logger.log(Level.WARN, "Error file. Deleting...", e);
                file.delete();
            }
        }
        return ret;
    }

    /**
     * Sets the first line of a file.
     *
     * @param file The file.
     * @param line The the value to set the line as.
     */
    public static void setFirstLine(File file, String line) {
        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(tryCreateFile(file)));
            writer.write(line);
            writer.flush();
            IOUtils.closeQuietly(writer);
        } catch (IOException e) {
            logger.log(Level.WARN, "Unable to write file.", e);
        }
    }

    /**
     * Attempts to create a file and all parent directories.
     *
     * @param file The file.
     * @return The same file.
     */
    public static File tryCreateFile(File file) {
        try {
            if (!file.exists()) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            }
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Unable to create new file.", e);
        }
    }

    /**
     * Counts the occurrence of a specific char inside the string.
     *
     * @param string The string to count chars in.
     * @param c      The char to count.
     * @return The number of occurrences inside the string.
     */
    public static int countChar(String string, char c) {
        int i = 0;
        for (char ch : string.toCharArray()) {
            if (ch == c) {
                i++;
            }
        }
        return i;
    }

    /**
     * Unzips the specified zip to the specified directory.
     *
     * @param zip        The zip to extract.
     * @param extractDir The directory to extract inside of.
     */
    public static void unzip(File zip, File extractDir) {
        try {
            ZipFile zipFile = new ZipFile(zip);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                InputStream in = zipFile.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(tryCreateFile(new File(extractDir, entry.getName())));
                IOUtils.copy(in, fos);
                IOUtils.closeQuietly(in, fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to extract zip.", e);
        }
    }

    /**
     * Process each entry inside a zip file, Useful for reading files without extracting.
     *
     * @param zip       The zip to read.
     * @param predicate If the entry should be processed.
     * @param consumer  The callback to process the entry.
     */
    public static void processZipFile(File zip, Predicate<ZipEntry> predicate, ThrowingBiConsumer<ZipFile, ZipEntry, IOException> consumer) {
        try {
            ZipFile zipFile = new ZipFile(zip);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (predicate.test(entry)) {
                    consumer.accept(zipFile, entry);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to extract zip.", e);
        }
    }

    /**
     * Delete a specific directory on disk.
     * Just a wrapper for Apache's that doesn't throw IOExceptions.
     *
     * @param dir The directory to delete.
     */
    public static void delete(File dir) {
        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to delete directory", e);
        }
    }

    /**
     * Process each line of a CSV file.
     *
     * @param csv      The CSV file to read.
     * @param consumer The callback for each line.
     */
    public static void readCsv(File csv, Consumer<String[]> consumer) {
        try {
            CSVReader reader = new CSVReader(new FileReader(csv));
            for (String[] line : reader.readAll()) {
                consumer.accept(line);
            }
            IOUtils.closeQuietly(reader);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read CSV file.", e);
        }
    }

    /**
     * Helper for a new PrintWriter to a file, assumes Charset of UTF8
     *
     * @param file The file to write to.
     * @return The Print writer.
     * @throws IOException If shit went down.
     */
    public static PrintWriter newPrintWriter(File file) throws IOException {
        return newPrintWriter(file, Charsets.UTF_8);
    }

    /**
     * Helper for a new PrintWriter to a file.
     *
     * @param file    The file to write to.
     * @param charset The charset to use.
     * @return The Print writer.
     * @throws IOException If shit went down.
     */
    public static PrintWriter newPrintWriter(File file, Charset charset) throws IOException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
    }

    /**
     * Creates a new reader from the given ZipFile and ZipEntry.
     * Just a helper.
     *
     * @param zipFile  The ZipFile.
     * @param zipEntry The ZipEntry.
     * @return The new Reader.
     * @throws IOException If shit went down.
     */
    public static Reader newReader(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
        return new InputStreamReader(zipFile.getInputStream(zipEntry));
    }

    /**
     * Checks if the line passed in is not null, empty or starting with a '#' symbol.
     * Useful for line parsing.
     * Basically "Dude, should i skip this line?"
     *
     * @param s The line to test.
     * @return If the line is NOT a comment, empty or null.
     */
    public static boolean notComment(String s) {
        return !Strings.isNullOrEmpty(s) && !s.startsWith("#");
    }
}
