package me.modmuss50.fastbuild;

import immibis.bon.ClassCollection;
import immibis.bon.IProgressListener;
import immibis.bon.ReferenceDataCollection;
import immibis.bon.Remapper;
import immibis.bon.gui.Operation;
import immibis.bon.io.ClassCollectionFactory;
import immibis.bon.io.JarWriter;
import immibis.bon.mcp.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This code is from https://github.com/immibis/bearded-octo-nemesis
 *
 *
 */
public class RebofUtils {

    public static void rebofJar(File dev, File output, String forgeinfo) throws Throwable {
        File homeDir = new File(System.getProperty("user.home"));
        File gradledir = new File(homeDir, ".gradle");
        File forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeinfo);
        File userDev = new File(forgeDir, "forge-" + forgeinfo + "-" + "-userdev.jsr");
        if (!userDev.exists()) {
            try{
                System.out.println("Downloading forge!");
                FileUtils.copyURLToFile(new URL("http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + forgeinfo + "/forge" + forgeinfo + "-userdev.jar"), userDev);
                System.out.println("Finished downloading forge");
            } catch (IOException ex){
                ex.printStackTrace();
                System.exit(0);
            }
        }

        FileInputStream userdevinput = new FileInputStream(userDev);

        Operation operation = Operation.ReobfuscateMod;

        SrgFile var43 = null;
        ExcFile errMsg = null;
        Map fieldsCSV = null;
        Map methodsCSV = null;
        String forgeVer = null;
        Throwable fgCacheDir = null;
        String mcVer = null;

        try {
            ZipInputStream side = new ZipInputStream(userdevinput);

            ZipEntry loader;
            try {
                while ((loader = side.getNextEntry()) != null) {
                    if (loader.getName().equals("conf/packaged.exc")) {
                        errMsg = ExcFile.read(side);
                    }

                    if (loader.getName().equals("conf/packaged.srg")) {
                        var43 = SrgFile.read(new InputStreamReader(side, StandardCharsets.UTF_8), false);
                    }

                    if (loader.getName().equals("conf/fields.csv")) {
                        fieldsCSV = CsvFile.read(new InputStreamReader(side, StandardCharsets.UTF_8), new int[]{2, 1, 0});
                    }

                    if (loader.getName().equals("conf/methods.csv")) {
                        methodsCSV = CsvFile.read(new InputStreamReader(side, StandardCharsets.UTF_8), new int[]{2, 1, 0});
                    }

                    if (loader.getName().startsWith("forge-") && loader.getName().endsWith("-changelog.txt")) {
                        forgeVer = loader.getName().substring(6, loader.getName().length() - 14);
                    }
                }
            } finally {
                if (side != null) {
                    side.close();
                }

            }
        } catch (Throwable var40) {
            if (fgCacheDir == null) {
                fgCacheDir = var40;
            } else if (fgCacheDir != var40) {
                fgCacheDir.addSuppressed(var40);
            }

            throw fgCacheDir;
        }

        if (var43 == null) {
            throw new Exception("conf/packaged.srg not found in Forge jar file");
        }

        if (errMsg == null) {
            throw new Exception("conf/packaged.exc not found in Forge jar file");
        }

        if (fieldsCSV == null) {
            throw new Exception("conf/fields.csv not found in Forge jar file");
        }

        if (methodsCSV == null) {
            throw new Exception("conf/methods.csv not found in Forge jar file");
        }

        if (forgeVer == null) {
            throw new Exception("unable to determine Forge version from jar file");
        }

        if (!forgeDir.isDirectory()) {
            throw new Exception("ForgeGradle cache directory doesn\'t exist: " + forgeDir + ". Is the directory set correctly, and is this version of Forge installed?");
        }

        mcVer = "unknown";
        immibis.bon.mcp.MinecraftNameSet.Side side = immibis.bon.mcp.MinecraftNameSet.Side.UNIVERSAL;
        MappingLoader_MCP mappingLoaderMcp = new MappingLoader_MCP();
        mappingLoaderMcp.load(side, mcVer, errMsg, var43, fieldsCSV, methodsCSV, new Proglistener());
        MappingFactory.registerMCPInstance(mcVer, side, mappingLoaderMcp);
        MinecraftNameSet refNS = new MinecraftNameSet(MinecraftNameSet.Type.MCP, side, mcVer);
        HashMap refCCList = new HashMap();
        File[] inputCC;
        int inputNS = (inputCC = new File[]{new File(forgeDir, "forgeSrc-" + forgeVer + ".jar")}).length;

        for (int inputType = 0; inputType < inputNS; ++inputType) {
            File remapTo = inputCC[inputType];
            System.err.println(remapTo.getAbsolutePath());
            refCCList.put(remapTo.getName(), ClassCollectionFactory.loadClassCollection(refNS, remapTo, new Proglistener()));
        }

        MinecraftNameSet.Type[] var47;
        MinecraftNameSet.Type var48;

        var48 = MinecraftNameSet.Type.MCP;//TODO check this
        var47 = new MinecraftNameSet.Type[]{MinecraftNameSet.Type.SRG};


        MinecraftNameSet var49 = new MinecraftNameSet(var48, side, mcVer);
        ClassCollection var50 = ClassCollectionFactory.loadClassCollection(var49, dev, new Proglistener());
        MinecraftNameSet.Type[] var21 = var47;
        int var20 = var47.length;

        for (int var19 = 0; var19 < var20; ++var19) {
            MinecraftNameSet.Type outputType = var21[var19];
            MinecraftNameSet outputNS = new MinecraftNameSet(outputType, side, mcVer);
            ArrayList remappedRefs = new ArrayList();
            Iterator var25 = refCCList.entrySet().iterator();

            while (var25.hasNext()) {
                Map.Entry e1 = (Map.Entry) var25.next();
                if (var50.getNameSet().equals(((ClassCollection) e1.getValue()).getNameSet())) {
                    remappedRefs.add(ReferenceDataCollection.fromClassCollection((ClassCollection) e1.getValue()));
                } else {
                    remappedRefs.add(ReferenceDataCollection.fromClassCollection(Remapper.remap((ClassCollection) e1.getValue(), MappingFactory.getMapping((MinecraftNameSet) ((ClassCollection) e1.getValue()).getNameSet(), (MinecraftNameSet) var50.getNameSet(), (IProgressListener) null), Collections.emptyList(), new Proglistener())));
                }
            }

            var50 = Remapper.remap(var50, MappingFactory.getMapping((MinecraftNameSet) var50.getNameSet(), outputNS, (IProgressListener) null), remappedRefs, new Proglistener());
            JarWriter.write(output, var50, new Proglistener());
        }

    }
}
