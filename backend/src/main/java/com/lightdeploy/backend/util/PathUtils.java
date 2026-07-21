package com.lightdeploy.backend.util;

import java.io.File;

public class PathUtils {

    public static String resolve(String path) {
        if (path == null) return null;
        String resolved = path;
        if (resolved.startsWith("~" + File.separator) || resolved.equals("~")) {
            resolved = System.getProperty("user.home") + resolved.substring(1);
        }
        File file = new File(resolved);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty("user.dir"), resolved);
        }
        return file.getAbsolutePath();
    }
}
