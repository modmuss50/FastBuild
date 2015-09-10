package me.modmuss50.fastbuild;

import com.github.parker8283.bon2.BON2Impl;
import com.github.parker8283.bon2.cli.CLIErrorHandler;
import com.github.parker8283.bon2.cli.CLIProgressListener;
import com.github.parker8283.bon2.data.IErrorHandler;
import com.github.parker8283.bon2.util.BONUtils;

import java.io.File;
import java.util.List;


public class RebofUtils {

    public static void rebofJar(File dev, File output, String forgeinfo) throws Throwable {
        IErrorHandler errorHandler = new CLIErrorHandler();
        List<String> mappings = BONUtils.buildValidMappings();
        String mapping = "";
        if (mappings.contains(forgeinfo + "-shipped")) {
            mapping = forgeinfo + "-shipped";
        }
        if (mapping.isEmpty()) {
            System.out.println("Could not find mappings!, this needs to be worked on");
        }
        BON2Impl.remap(dev, output, mapping, errorHandler, new CLIProgressListener());
    }

}
