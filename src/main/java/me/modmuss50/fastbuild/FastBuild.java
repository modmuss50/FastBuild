package me.modmuss50.fastbuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.modmuss50.fastbuild.buildScripts.BuildInfo;
import me.modmuss50.fastbuild.mcForge.Library;

import java.io.*;
import java.util.ArrayList;

public class FastBuild {

    public static boolean isJenkins = false;
    public static boolean isOffline = false;
    public static boolean isModmussJenkins = false;

    public static void main(String[] args) throws Throwable {
        for (String arg : args) {
            if (arg.endsWith("-jenkins")) {
                isJenkins = true;
            }
            if (arg.endsWith("-offline")) {
                isOffline = true;
            }
            if (arg.endsWith("-modmuss50Jenkins")) {
                isModmussJenkins = true;
            }
        }
        Main main = new Main();
        File buildinfo = new File("build.json");
        BuildInfo info = null;
        if (buildinfo.exists()) {
            try {
                BufferedReader br = new BufferedReader(
                        new FileReader(buildinfo));
                Gson gson = new Gson();
                info = gson.fromJson(br, BuildInfo.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Could not find a build.json file, will now make one and exit");
            info = new BuildInfo();
            info.forgeVersion = "1.7.10-10.13.4.1481-1.7.10";
            info.version = "1.7.10";
            info.projectName = "Project";

            Library library = new Library();
            library.name = "modDev.jar";
            library.url = "http://somesite.com/modDev.jar";
            info.libraries = new ArrayList<>();
            info.libraries.add(library);
            info.devJar = true;
            info.srcJar = false;

            info.manifest = new ArrayList<>();
            info.manifest.add("Manifest-Version: 1.0");

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(info);
            FileWriter writer = new FileWriter(buildinfo);
            writer.write(json);
            writer.close();
            System.exit(0);
        }
        if (info == null) {
            throw new Exception("Could not load the json file");
        }
        if (isJenkins) {
            String buildNumber = System.getenv("BUILD_NUMBER");
            if (buildinfo != null) {
                info.version = info.version.replaceAll("%BUILD%", buildNumber);
            } else {
                throw new NullPointerException("Could not find the build number from jenkins, are you sure this is jenkins?");
            }
        }

        System.out.println("Building " + info.projectName + " version " + info.version);
        if (isJenkins) {
            System.out.println("This is a jenkins build!");
        }
        main.run(info);
    }
}
