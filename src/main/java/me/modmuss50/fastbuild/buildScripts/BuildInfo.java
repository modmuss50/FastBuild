package me.modmuss50.fastbuild.buildScripts;

import me.modmuss50.fastbuild.mcForge.Library;

import java.util.List;

public class BuildInfo {
    public String projectName;
    public String version;

    public boolean useForge = true;
    public String forgeVersion;

    public List<Library> libraries;
    public String bytecodeVersion = "1.7";
    public List<String> manifest;

    public boolean devJar;
    public boolean srcJar;
    public boolean uniJar = true;
}
