package me.modmuss50.fastbuild;

import com.github.parker8283.bon2.cli.CLIErrorHandler;
import com.github.parker8283.bon2.cli.CLIProgressListener;
import com.github.parker8283.bon2.data.IErrorHandler;
import com.github.parker8283.bon2.data.IProgressListener;
import com.github.parker8283.bon2.srg.ClassCollection;
import com.github.parker8283.bon2.srg.Repo;
import com.github.parker8283.bon2.util.JarUtils;
import com.github.parker8283.bon2.util.Remapper;

import java.io.File;
import java.io.IOException;


public class RebofUtils {

    public static void rebofJar(File dev, File output, String forgeinfo) throws Throwable {
        IErrorHandler errorHandler = new CLIErrorHandler();
        File homeDir = new File(System.getProperty("user.home"));
        File gradledir = new File(homeDir, ".gradle");
        File forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeinfo);
        File srg = new File(forgeDir, "srgs");
        if (!srg.exists()) {
            System.out.println("Could not find mappings, bad things are about to happen");
        }
        remap(dev, output, srg, errorHandler, new CLIProgressListener());
    }


    public static void remap(File inputJar, File outputJar, File mappings, IErrorHandler errorHandler, IProgressListener progressListener) throws IOException {
        System.out.println("Loading mappings");
        Repo.loadMappings(mappings, progressListener);
        ClassCollection inputCC = JarUtils.readFromJar(inputJar, errorHandler, progressListener);
        System.out.println("Remapping");
        ClassCollection outputCC = Remapper.remap(inputCC, progressListener);
        JarUtils.writeToJar(outputCC, outputJar, progressListener);
        System.out.println("Remapped jar");
    }

}
