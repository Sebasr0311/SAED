package org.apache.maven.wrapper;

import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.util.Properties;

public class MavenWrapperDownloader {
    private static final String WRAPPER_VERSION = "3.2.0";
    private static final String PROPERTIES_FILE = "maven-wrapper.properties";
    private static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    private static final String WRAPPER_URL_PROPERTY = "wrapperUrl";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("-DgroupId=... -DartifactId=... -Dversion=...");
            return;
        }
        File wrapperJar = new File(args[0]);
        if (wrapperJar.exists()) {
            System.out.println("- File '" + wrapperJar.getAbsolutePath() + "' already exists, skipping download");
            return;
        }
        Properties properties = new Properties();
        try (InputStream is = MavenWrapperDownloader.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) properties.load(is);
        } catch (IOException e) {
            System.err.println("Could not load " + PROPERTIES_FILE);
        }
        String wrapperUrl = properties.getProperty(WRAPPER_URL_PROPERTY,
            "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/" + WRAPPER_VERSION + "/maven-wrapper-" + WRAPPER_VERSION + ".jar");
        try {
            downloadFileFromURL(wrapperUrl, wrapperJar);
            System.out.println("Downloaded " + wrapperJar.getName());
        } catch (IOException e) {
            System.err.println("- Error downloading: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void downloadFileFromURL(String urlString, File destination) throws IOException {
        URL url = new URL(urlString);
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
             FileOutputStream fos = new FileOutputStream(destination)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }
}
