package me.modmuss50.fastbuild;

import com.google.common.base.Throwables;
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
        File tempsrc = new File(buildDir, "tempsrc");
        File resDir = new File("src/main/resources");
        File sources = new File("src/main/java");
        File jarOut = new File(buildDir, "libs");
        if (!buildDir.exists()) {
            buildDir.mkdir();
        }
        outputDir.mkdir();
        if (!sources.exists()) {
            System.out.println("Could not find mod sources!");
            return;
        }
        tempsrc.mkdir();
        FileUtils.copyDirectory(sources, tempsrc);
        if (resDir.exists()) {
            FileUtils.copyDirectory(resDir, tempsrc);
        }
        if (jarOut.exists()) {
            deleteFolder(jarOut);
        }
        jarOut.mkdir();

        ArrayList<String> javaSources = new ArrayList<>();
        try {
            Files.walk(Paths.get(tempsrc.getAbsolutePath())).forEach(filePath -> {
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

        System.out.println("Creating jar's file");

        if (resDir.exists()) {
            System.out.println("Copying the resources!");
            FileUtils.copyDirectory(resDir, outputDir);
        }

        File devJar = new File(jarOut, info.projectName + "-" + info.version + "-dev.jar");
        if (devJar.exists()) {
            devJar.delete();
        }
        ZipUtil.pack(outputDir, devJar);

        if (info.uniJar) {
            File releaseJar = new File(jarOut, info.projectName + "-" + info.version + "-univseral.jar");
            if (releaseJar.exists()) {
                releaseJar.delete();
            }

            RebofUtils.rebofJar(devJar, releaseJar, forgeIdentifyer);
        }

        if (!info.devJar) {
            devJar.delete();
        }

        File srcJar = new File(jarOut, info.projectName + "-" + info.version + "-src.jar");
        if (srcJar.exists()) {
            srcJar.delete();
        }

        if (info.srcJar) {
            ZipUtil.pack(tempsrc, srcJar);
        }

        deleteFolder(tempsrc);
        deleteFolder(outputDir);
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


    public void compileJavaFile(BuildInfo info) throws IOException {

        File buildDir = new File("build");
        File outputDir = new File(buildDir, "outputs");
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdir();

        List<String> commandargs = new ArrayList<String>();
        commandargs.add(" -d " + outputDir.getAbsolutePath());

        commandargs.add(" -1.7");

        commandargs.add(" -warn:none");

        File homeDir = new File(System.getProperty("user.home"));
        File gradledir = new File(homeDir, ".gradle");
        if (!gradledir.exists()) {
            System.out.println("You need to setup a dev env to use this using gradle!!!");
            System.exit(1);
        }

        File forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeIdentifyer);
        if (!forgeDir.exists()) {
            System.out.println("You need to setup a dev env to use this using gradle!!!");
            System.exit(1);
        }

        File devJson = new File(forgeDir, "unpacked/dev.json");
        if (!devJson.exists()) {
            System.out.println("Could not find a dev.json file");
            System.exit(1);
        }

        File filestore = new File(gradledir, "caches/artifacts-24/filestore");
        if (!filestore.exists()) {
            if (new File(gradledir, "caches/modules-2/files-2.1").exists()) {
                filestore = new File(gradledir, "caches");
            } else {
                filestore = new File(gradledir, "caches");
                if (!filestore.exists()) {
                    System.out.println("Could not find Gradle caches folder!");
                    System.exit(1);
                } else {
                    System.out.println("The system could not find the filestore, it will try and look, this will take a bit longer!");
                }
            }
        }

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
                        System.exit(1);
                    }
                }
                libs.add(lib);
            }
        }
        commandargs.add(" -O -time -progress -noExit");
        commandargs.add(" -classpath ");

        String libarg = "";
        File forgeSrc = new File(forgeDir, "forgeSrc-" + forgeIdentifyer + ".jar");
        if (!forgeSrc.exists()) {
            forgeSrc = new File(forgeDir, "forgeBin-" + forgeIdentifyer + ".jar");
            if (!forgeSrc.exists()) {
                System.out.println("You need to setup gradle!");
                System.exit(1);
            }
        }
        libarg = libarg + forgeSrc.getCanonicalPath() + ";";
        for (File lib : libs) {
            libarg = libarg + lib.getCanonicalPath() + ";";
        }
        if (libarg.length() > 0 && libarg.charAt(libarg.length() - 1) == ';') {
            libarg = libarg.substring(0, libarg.length() - 1);
        }
        commandargs.add(libarg);

       // File sources = new File("src/main/java");
        File tempsrc = new File(buildDir, "tempsrc");
        commandargs.add(" " + tempsrc.getAbsolutePath());
        String[] commands = new String[commandargs.size()];
        commands = commandargs.toArray(commands);
        StringBuilder builder = new StringBuilder();
        for (String s : commands) {
            builder.append(s);
        }
        CompilationProgress progress = null;
        System.out.println("Starting build");
        //System.out.println(builder.toString());
        if (!BatchCompiler.compile(builder.toString(), new PrintWriter(System.out), new PrintWriter(System.out), progress)) {

            System.out.println("Failed to build");
            System.exit(1);
        }

    }

    public static int runProcess(File workDir, List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));

        final Process ps = pb.start();

        new Thread(new StreamRedirector(ps.getInputStream(), System.out)).start();
        new Thread(new StreamRedirector(ps.getErrorStream(), System.err)).start();

        int status = ps.waitFor();

        if (status != 0) {
            throw new RuntimeException("Error running command, return status !=0: " + command.toString());
        }

        return status;
    }

    private static class StreamRedirector implements Runnable {

        private final InputStream in;
        private final PrintStream out;

        public StreamRedirector(InputStream in, PrintStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    out.println(line);
                }
            } catch (IOException ex) {
                throw Throwables.propagate(ex);
            }
        }
    }
}
