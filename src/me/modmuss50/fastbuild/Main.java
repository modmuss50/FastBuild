package me.modmuss50.fastbuild;

import com.google.gson.Gson;
import me.modmuss50.fastbuild.mcForge.Library;
import me.modmuss50.fastbuild.mcForge.Version;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static String forgeIdentifyer = "1.7.10-10.13.4.1481-1.7.10";

    public static void main(String[] args) throws Throwable {
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
        compileJavaFile(javaSources, buildDir);

        System.out.println("Creating zip file");

        File resDir = new File("src/main/resources");
        if (resDir.exists()) {
            System.out.println("Copying the resources!");
            FileUtils.copyDirectory(resDir, outputDir);
        }

        File devJar = new File(buildDir, "dev.jar");
        if (devJar.exists()) {
            devJar.delete();
        }
        ZipUtil.pack(outputDir, devJar);

        //OBOFED ZIP

        File releaseJar = new File(buildDir, "univseral.jar");
        if (releaseJar.exists()) {
            releaseJar.delete();
        }

        String mappingsVer = forgeIdentifyer + "-shipped";

        RebofUtils.rebofJar(devJar, releaseJar, forgeIdentifyer);

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


    public static void compileJavaFile(List<String> inputs, File output) {

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
        BatchCompiler.compile(builder.toString(), new PrintWriter(System.out), new PrintWriter(System.out), progress);
        System.out.println("Built the mod!");

    }
}
