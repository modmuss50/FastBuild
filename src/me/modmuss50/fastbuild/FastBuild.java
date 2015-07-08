package me.modmuss50.fastbuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.modmuss50.fastbuild.buildScripts.BuildInfo;
import me.modmuss50.fastbuild.mcForge.Library;

import java.io.*;
import java.util.ArrayList;

public class FastBuild {

    public static void main(String[] args) throws Throwable {
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
            library.disableSSLCert = false;
            info.libraries = new ArrayList<>();
            info.libraries.add(library);
            info.devJar = true;
            info.srcJar = false;

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(info);
            FileWriter writer = new FileWriter(buildinfo);
            writer.write(json);
            writer.close();
            System.exit(0);
        }
        if (info == null) {
            System.out.println("An error has occurred!");
            System.exit(0);
        }
        main.run(info);
    }
}
