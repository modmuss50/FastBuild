package me.modmuss50.fastbuild;

import com.google.common.io.Files;
import me.modmuss50.fastbuild.mcForge.util.ReobfExceptor;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


public class RebofUtils {

    public static void rebofJar(File dev, File output, String forgeinfo, Main main, List<File> files) throws Throwable {
        File homeDir = new File(System.getProperty("user.home"));
        File gradledir = new File(homeDir, ".gradle");
        File forgeDir = new File(gradledir, "caches/minecraft/net/minecraftforge/forge/" + forgeinfo);
        File srg = new File(forgeDir, "srgs/mcp-srg.srg");
        if (!srg.exists()) {
            System.out.println("Could not find mappings, bad things are about to happen");
        }
        File conf = new File(forgeDir, "unpacked/conf");
        if(!conf.exists()){
            System.out.println("Could not find conf folder, something bad will happen");
        }

        File exc = new File(conf, "packaged.exc");
        File fieldsCsv = new File(conf, "fields.csv");
        File methodsCsv = new File(conf, "methods.csv");

        ReobfExceptor exceptor = new ReobfExceptor();
        exceptor.toReobfJar = output;
        exceptor.deobfJar = dev;
        exceptor.excConfig = exc;
        exceptor.fieldCSV = fieldsCsv;
        exceptor.methodCSV = methodsCsv;

        File tempSpeicalSource = new File(main.fastBuildCache, "tempSpecialSource");
        if(tempSpeicalSource.exists()){
            Main.deleteFolder(tempSpeicalSource);
        }
        tempSpeicalSource.mkdir();
        File outSrg =  new File(tempSpeicalSource, "reobf_cls.srg");

        System.out.println("Applying SpecialSource");
        exceptor.doFirstThings();
        exceptor.buildSrg(srg, outSrg);

        srg = outSrg;
        BufferedWriter writer = new BufferedWriter(new FileWriter(srg, true));
        writer.flush();
        writer.close();
        obfuscate(dev, files, srg, output);
    }

    private static void obfuscate(File inJar, List<File> files, File srg, File output) throws IOException {
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(Files.newReader(srg, Charset.defaultCharset()), null, null, false);
        JarRemapper remapper = new JarRemapper(null, mapping);
        Jar input = Jar.init(inJar);
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));
        if (!files.isEmpty()){
            inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(toUrls(files))));
        }
        mapping.setFallbackInheritanceProvider(inheritanceProviders);
        File out = output;
        if (!out.getParentFile().exists())
        {
            out.getParentFile().mkdirs();
        }
        remapper.remapJar(input, output);
    }

    public static URL[] toUrls(List<File> files) throws MalformedURLException
    {
        ArrayList<URL> urls = new ArrayList<URL>();
        for (File file : files){
            if(file.exists()){
                urls.add(file.toURI().toURL());
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

}
