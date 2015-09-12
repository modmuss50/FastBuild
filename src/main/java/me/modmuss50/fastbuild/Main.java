package me.modmuss50.fastbuild;

import com.google.gson.Gson;
import me.modmuss50.fastbuild.buildScripts.BuildInfo;
import me.modmuss50.fastbuild.mcForge.Library;
import me.modmuss50.fastbuild.mcForge.Version;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public String forgeIdentifyer;

    ArrayList<File> libFiles = new ArrayList<>();

    File fastBuildCache;

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

    public void run(BuildInfo info) throws Throwable {
        this.forgeIdentifyer = info.forgeVersion;
        Instant start = Instant.now();
        File runDir = new File(".");
        File buildDir = new File("build");
        File outputDir = new File(buildDir, "outputs");
        File tempsrc = new File(buildDir, "tempsrc");
        File resDir = new File("src/main/resources");
        File sources = new File("src/main/java");
        File jarOut = new File(buildDir, "libs");
        File homeDir = new File(System.getProperty("user.home"));
        fastBuildCache = new File(homeDir, ".fastbuild");
        if (!fastBuildCache.exists()) {
            fastBuildCache.mkdir();
        }

        if (!buildDir.exists()) {
            buildDir.mkdir();
        }
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdir();
        if (!sources.exists()) {
            System.out.println("Could not find sources!");
            return;
        }
        if (tempsrc.exists()) {
            deleteFolder(tempsrc);
        }
        tempsrc.mkdir();
        FileUtils.copyDirectory(sources, tempsrc);
        if(info.versionStrings != null || !info.versionStrings.isEmpty()){
            ArrayList<File> filesToScanAndCopy = new ArrayList<>();
            ArrayList<File> filesToCopy = new ArrayList<>();
            listFilesAndSub(tempsrc, filesToScanAndCopy, filesToCopy);
            for(File source : filesToScanAndCopy){
                System.out.println(source.getAbsolutePath().replace(runDir.getAbsolutePath(), ""));
                Path path = Paths.get(source.getAbsolutePath().replace(runDir.getAbsolutePath(), ""));
                Charset charset = StandardCharsets.UTF_8;

                String content = new String(Files.readAllBytes(path), charset);
                for(String string : info.versionStrings){
                    content = content.replaceAll(string, info.version);
                    System.out.println(content);
                }

                Files.write(path, content.getBytes(charset));
            }
        }

        if (resDir.exists()) {
            FileUtils.copyDirectory(resDir, tempsrc);
        }
        if (jarOut.exists()) {
            deleteFolder(jarOut);
        }
        jarOut.mkdir();

        compileJavaFile(info);

        System.out.println("Creating jar's file");

        if (resDir.exists()) {
            System.out.println("Copying the resources!");
            FileUtils.copyDirectory(resDir, outputDir);
        }

        for (Library library : info.libraries) {
            if (library.copyToJar) {
                for (File libFile : libFiles) {
                    if (libFile.getName().equals(library.name)) {
                        ZipUtil.unpack(libFile, outputDir);
                    }
                }
            }
        }

        if (info.manifest != null && !info.manifest.isEmpty()) {
            File metaInf = new File(outputDir, "META-INF");
            if (!metaInf.exists()) {
                metaInf.mkdir();
            }
            File manifestFile = new File(metaInf, "MANIFEST.MF");
            if (manifestFile.exists()) {
                manifestFile.delete();
            }
            PrintWriter out = new PrintWriter(manifestFile);
            for (String string : info.manifest) {
                out.println(string);
            }
            out.close();
        }

        File devJar = new File(jarOut, info.projectName + "-" + info.version + "-dev.jar");
        if (devJar.exists()) {
            devJar.delete();
        }
        ZipUtil.pack(outputDir, devJar);

        if (info.uniJar && info.useForge) {
            File releaseJar = new File(jarOut, info.projectName + "-" + info.version + "-univseral.jar");
            if (releaseJar.exists()) {
                releaseJar.delete();
            }
            copyFile(devJar, releaseJar);

            RebofUtils.rebofJar(devJar, releaseJar, forgeIdentifyer, this, libFiles);
        }

        if (!info.useForge) {
            File jarFile = new File(jarOut, info.projectName + "-" + info.version + ".jar");
            FileUtils.copyFile(devJar, jarFile);
        }

        if (!info.devJar || !info.useForge) {
            devJar.delete();
        }

        File srcJar = new File(jarOut, info.projectName + "-" + info.version + "-src.jar");
        if (srcJar.exists() || !info.useForge) {
            srcJar.delete();
        }

        if (info.srcJar) {
            ZipUtil.pack(tempsrc, srcJar);
        }

        if (info.apiJar) {
            System.out.println("Making api jar");
            if (info.apiPackage == "") {
                throw new Exception("Api Package cannot be empty");
            }
            File apiTemp = new File(jarOut, "api");
            if (apiTemp.exists()) {
                deleteFolder(apiTemp);
            }
            apiTemp.mkdir();
            String packagePath = info.apiPackage;
            File apiPackage = new File(apiTemp, packagePath);
            apiPackage.mkdir();
            File apiSource = new File(sources, packagePath);
            if (!apiSource.exists()) {
                throw new Exception("Could not find the sources for the api");
            }
            File apiClasses = new File(outputDir, packagePath);
            if (!apiClasses.exists()) {
                throw new Exception("Could not find the compiled java code for the api");
            }
            FileUtils.copyDirectory(apiSource, apiPackage);
            FileUtils.copyDirectory(apiClasses, apiPackage);
            File apiZip = new File(jarOut, info.projectName + "-" + info.version + "-api.jar");
            ZipUtil.pack(apiTemp, apiZip);
            deleteFolder(apiTemp);
        }

        deleteFolder(tempsrc);
        deleteFolder(outputDir);
        Instant end = Instant.now();
        System.out.println("Took " + Duration.between(start, end).getSeconds() + " seconds to build");
    }

    public void compileJavaFile(BuildInfo info) throws Exception {

        File buildDir = new File("build");
        File outputDir = new File(buildDir, "outputs");
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdir();

        List<String> commandargs = new ArrayList<String>();
        commandargs.add(" -d " + outputDir.getAbsolutePath());

        commandargs.add(" -" + info.bytecodeVersion);

        commandargs.add(" -warn:none");

        File homeDir = new File(System.getProperty("user.home"));


        File forgeDir = null;

        if (info.useForge) {
            File gradledir = new File(homeDir, ".gradle");
            if (!gradledir.exists()) {
                throw new Exception("Could not find a gradle caches folder");
            }

            forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeIdentifyer);
            if (!forgeDir.exists()) {
                System.out.println("You need to setup a dev env to use this using gradle!!!");
                throw new Exception("Could not find " + forgeIdentifyer + " gradle files");
            }

            File devJson = new File(forgeDir, "unpacked/dev.json");
            if (!devJson.exists()) {
                System.out.println("Could not find a dev.json file");
                throw new FileNotFoundException("coudl not find dev.json");
            }

            File filestore = new File(gradledir, "caches/artifacts-24/filestore");
            if (!filestore.exists()) {
                if (new File(gradledir, "caches/modules-2/files-2.1").exists()) {
                    filestore = new File(gradledir, "caches");
                } else {
                    filestore = new File(gradledir, "caches");
                    if (!filestore.exists()) {
                        throw new FileNotFoundException("Could not find Gradle caches folder!");
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

            try {
                Files.walk(Paths.get(filestore.getAbsolutePath())).forEach(filePath -> {
                    if (Files.isRegularFile(filePath)) {
                        if (neededLibs.contains(filePath.toFile().getName())) {
                            libFiles.add(filePath.toFile());
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        File libDir = new File(fastBuildCache, "deps");
        if (!libDir.exists()) {
            libDir.mkdir();
        }
        if (info.libraries.size() != 0) {
            for (Library library : info.libraries) {
                File lib = new File(libDir, library.name);
                if (!lib.exists()) {
                    System.out.println("Downloading library " + library.name);
                    if(FastBuild.isModmussJenkins){
                        library.url = library.url.replace("modmuss50.me", "localhost");
                    }
                    try {
                        FileUtils.copyURLToFile(new URL(library.url), lib);
                    } catch (IOException e) {
                        System.out.println("Failed to download a library!");
                        e.printStackTrace();
                    }
                }
                libFiles.add(lib);
            }
        }
        commandargs.add(" -O -time -progress -noExit");
        commandargs.add(" -classpath ");

        String seperator = ":";
        if (System.getProperty("os.name").startsWith("Windows")) {
            seperator = ";";
        }
        StringBuffer libarg = new StringBuffer();

        if (info.useForge) {
            if (forgeDir == null) {
                throw new NullPointerException("Could not find forge!");
            }
            File forgeSrc = new File(forgeDir, "forgeSrc-" + forgeIdentifyer + ".jar");
            if (!forgeSrc.exists()) {
                forgeSrc = new File(forgeDir, "forgeBin-" + forgeIdentifyer + ".jar");
                if (!forgeSrc.exists()) {
                    System.out.println("You need to setup gradle!");
                    System.exit(1);
                }
            }
            libarg.append(forgeSrc.getAbsolutePath() + seperator);
        }

        for (File lib : libFiles) {
            if (lib.exists()) {
                libarg.append(lib.getAbsolutePath() + seperator);
            } else {
                System.out.println("Could not find lib, will still try and compile");
            }

        }
        commandargs.add(libarg.toString());

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
        if (!BatchCompiler.compile(builder.toString(), new PrintWriter(System.out), new PrintWriter(System.out), progress)) {
            throw new Exception("Failed to build");
        }
    }

    private  void copyFile(File srcFile, File destFile) throws IOException
    {
        InputStream oInStream = new FileInputStream(srcFile);
        OutputStream oOutStream = new FileOutputStream(destFile);

        // Transfer bytes from in to out
        byte[] oBytes = new byte[1024];
        int nLength;
        BufferedInputStream oBuffInputStream =
                new BufferedInputStream( oInStream );
        while ((nLength = oBuffInputStream.read(oBytes)) > 0)
        {
            oOutStream.write(oBytes, 0, nLength);
        }
        oInStream.close();
        oOutStream.close();
    }

    public void listFilesAndSub(File start, ArrayList<File> sources, ArrayList<File> resources){
        File[] flist = start.listFiles();
        for(File file : flist){
            if(file.isFile()){
                if(file.getName().endsWith(".png") || file.getName().endsWith(".class") || file.getName().endsWith(".jar")){
                    resources.add(file);
                } else {
                    sources.add(file);
                }
            } else if(file.isDirectory()){
                listFilesAndSub(file, sources, resources);
            }
        }
    }
}

