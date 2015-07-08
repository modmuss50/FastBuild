package me.modmuss50.fastbuild;

import com.google.gson.Gson;
import me.modmuss50.fastbuild.buildScripts.BuildInfo;
import me.modmuss50.fastbuild.mcForge.Library;
import me.modmuss50.fastbuild.mcForge.Version;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.zeroturnaround.zip.ZipUtil;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public String forgeIdentifyer;

    public void run(BuildInfo info) throws Throwable {
        this.forgeIdentifyer = info.forgeVersion;
        Instant start = Instant.now();
        File buildDir = new File("build");
        File outputDir = new File(buildDir, "outputs");
        File sources = new File("src/main/java");
        if (!buildDir.exists()) {
            buildDir.mkdir();
        }
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdir();
        if (!sources.exists()) {
            System.out.println("Could not find mod sources!");
            return;
        }

        ArrayList<String> javaSources = new ArrayList<>();

        try {
            Files.walk(Paths.get(sources.getAbsolutePath())).forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {
                    if (filePath.toFile().getName().endsWith(".java")) {
                        javaSources.add(filePath.toString() + " ");
                    }

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Compiling " + javaSources.size() + " java files");
        compileJavaFile(info);

        System.out.println("Creating zip file");

        File resDir = new File("src/main/resources");
        if (resDir.exists()) {
            System.out.println("Copying the resources!");
            FileUtils.copyDirectory(resDir, outputDir);
        }

        File devJar = new File(buildDir, info.projectName + "-" + info.version + "-dev.jar");
        if (devJar.exists()) {
            devJar.delete();
        }
        ZipUtil.pack(outputDir, devJar);

        //OBOFED ZIP

        File releaseJar = new File(buildDir, info.projectName + "-" + info.version + "-univseral.jar");
        if (releaseJar.exists()) {
            releaseJar.delete();
        }

        RebofUtils.rebofJar(devJar, releaseJar, forgeIdentifyer);

        if (!info.devJar) {
            devJar.delete();
        }

        File srcJar = new File(buildDir, info.projectName + "-" + info.version + "-src.jar");
        if(srcJar.exists()){
            srcJar.delete();
        }

        if(info.srcJar){
            ZipUtil.pack(sources, srcJar);
        }

        Instant end = Instant.now();
        System.out.println("Took " + Duration.between(start, end).getSeconds() + " seconds to build");
    }

    public static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }


    public void compileJavaFile(BuildInfo info) throws MalformedURLException {

        File buildDir = new File("build");
        File outputDir = new File(buildDir, "outputs");
        outputDir.delete();
        outputDir.mkdir();

        List<String> commandargs = new ArrayList<String>();
        commandargs.add(" -d " + outputDir.getAbsolutePath());

        commandargs.add(" -1.7");

        commandargs.add(" -warn:none");

        File homeDir = new File(System.getProperty("user.home"));
        File gradledir = new File(homeDir, ".gradle");
        if (!gradledir.exists()) {
            System.out.println("You need to setup a dev env to use this using gradle!!!");
            System.exit(0);
        }

        File forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeIdentifyer);
        if (!forgeDir.exists()) {
            System.out.println("You need to setup a dev env to use this using gradle!!!");
            System.exit(0);
        }

        File devJson = new File(forgeDir, "unpacked/dev.json");
        if (!devJson.exists()) {
            System.out.println("Could not find a dev.json file");
            System.exit(0);
        }

        File filestore = new File(gradledir, "caches/artifacts-24/filestore");

        ArrayList<String> neededLibs = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(
                    new FileReader(devJson));
            Gson gson = new Gson();
            Version obj = gson.fromJson(br, Version.class);
            for (Library library : obj.libraries) {
                String[] name = library.name.split(":");
                neededLibs.add(name[1] + "-" + name[2] + ".jar");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        neededLibs.add("launchwrapper-1.11.jar");
        ArrayList<File> libs = new ArrayList<>();
        try {
            Files.walk(Paths.get(filestore.getAbsolutePath())).forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {
                    if (neededLibs.contains(filePath.toFile().getName())) {
                        libs.add(filePath.toFile());
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        File libDir = new File(buildDir, "deps");
        if (!libDir.exists()) {
            libDir.mkdir();
        }
        boolean hasFixedHttps = false;
        if (info.libraries.size() != 0) {
            for (Library library : info.libraries) {
                File lib = new File(libDir, library.name);
                if (!lib.exists()) {
                    System.out.println("Downloading library " + library.name);
                    try {
                        if (library.disableSSLCert && hasFixedHttps == false) {
                            System.out.println("One or more libs has asked to disable ssl!!!");
                            /**
                             * This should only be used if you know what you are doing
                             */
                            //TODO find a better way of doing this, this is BAD!!!
                            final TrustManager[] trustAllCertificates = new TrustManager[]{
                                    new X509TrustManager() {
                                        @Override
                                        public X509Certificate[] getAcceptedIssuers() {
                                            return null; // Not relevant.
                                        }

                                        @Override
                                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                                            // Do nothing. Just allow them all.
                                        }

                                        @Override
                                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                                            // Do nothing. Just allow them all.
                                        }
                                    }
                            };

                            try {
                                SSLContext sc = SSLContext.getInstance("SSL");
                                sc.init(null, trustAllCertificates, new SecureRandom());
                                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                            } catch (GeneralSecurityException e) {
                                throw new ExceptionInInitializerError(e);
                            }
                        }
                        FileUtils.copyURLToFile(new URL(library.url), lib);
                    } catch (IOException e) {
                        System.out.println("Failed to download a library!");
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
                libs.add(lib);
            }
        }

        commandargs.add(" -classpath ");
        String libarg = "";
        File forgeSrc = new File(forgeDir, "forgeSrc-" + forgeIdentifyer + ".jar");
        libarg = libarg + forgeSrc.getAbsolutePath() + ";";
        for (File lib : libs) {
            libarg = libarg + lib.getAbsolutePath() + ";";
        }
        if (libarg.length() > 0 && libarg.charAt(libarg.length() - 1) == ';') {
            libarg = libarg.substring(0, libarg.length() - 1);
        }
        commandargs.add(libarg);

        File sources = new File("src/main/java");
        commandargs.add(" " + sources.getAbsolutePath());
        String[] commands = new String[commandargs.size()];
        commands = commandargs.toArray(commands);
        StringBuilder builder = new StringBuilder();
        for (String s : commands) {
            builder.append(s);
        }
        CompilationProgress progress = null;
        System.out.println("Starting build");

        if (!BatchCompiler.compile(builder.toString(), new PrintWriter(System.out), new PrintWriter(System.out), progress)) {
            System.out.println("Failed to build");
            System.exit(0);
        }
    }
}
