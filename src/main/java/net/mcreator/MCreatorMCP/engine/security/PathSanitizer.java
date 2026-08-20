package net.mcreator.MCreatorMCP.engine.security;

import java.io.File;
import java.io.IOException;

public class PathSanitizer {

    public static File sanitizePath(File workspaceFolder, String relativeOrAbsolutePath) throws IOException {
        if (workspaceFolder == null) {
            throw new IOException("Workspace root is null");
        }
        File target = new File(relativeOrAbsolutePath);
        if (!target.isAbsolute()) {
            target = new File(workspaceFolder, relativeOrAbsolutePath);
        }

        String canonicalRoot = workspaceFolder.getCanonicalPath();
        String canonicalTarget = target.getCanonicalPath();

        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw new SecurityException("Access Denied: Attempted path traversal outside workspace: " + relativeOrAbsolutePath);
        }
        return target;
    }
}
