package io.github.otavia.jni.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class NativeLoader {

    public NativeLoader(String library) throws IOException {
        NativeLoader.load(library);
    }

    private static final ConcurrentHashMap<String, Boolean> loaded = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> failure = new ConcurrentHashMap<>();

    public static void load(String library) throws IOException {
        String lib = System.mapLibraryName(library);
        String resourcePath = "/native/" + NativeLoader.getCurrentTargetName() + "/" + lib;

        if (!loaded.containsKey(library) && !failure.containsKey(library)) {
            Path tmp = Files.createTempDirectory("rust-jni-");
            Path extractedPath = tmp.resolve(lib);
            InputStream resourceStream = NativeLoader.class.getResourceAsStream(resourcePath);
            if (resourceStream == null) {
                try {
                    System.loadLibrary(library);
                } catch (Exception ex) {
                    failure.put(library, true);
                    throw new UnsatisfiedLinkError("Native library " + lib +
                            " (" + resourcePath + ") cannot be found on the classpath." +
                            " And also can't find native library [" + lib +
                            "] from java.library.path " + ex.getMessage());
                }
                loaded.put(library, true);
            } else {
                try {
                    Files.copy(resourceStream, extractedPath);
                    System.load(extractedPath.toAbsolutePath().toString());
                } catch (Exception ex) {
                    failure.put(library, true);
                    throw new UnsatisfiedLinkError("Error while extracting native library: " + ex);
                }
                loaded.put(library, true);
            }
        } else if (failure.containsKey(library)) {
            throw new UnsatisfiedLinkError(
                    "Native library " + lib + " (" + resourcePath + ") cannot be found on the classpath."
            );
        }
    }


    private static String getCurrentTargetName() {
        String target = System.getenv("RUN_RUST_TARGET");
        if (target == null) {
            return targetName0();
        } else {
            return target;
        }
    }

    private static String targetName0() {
        String os = toRustOS(System.getProperty("os.name").toLowerCase());
        String arch = toRustArch(System.getProperty("os.arch"));
        return arch + os;
    }

    private static String toRustArch(String arch) {
        if (arch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        } else if (arch.trim().equals("aarch64")) {
            return "aarch64";
        } else {
            return arch;
        }
    }

    private static String toRustOS(String os) {
        if (os.contains("windows")) {
            return "-pc-windows-msvc";
        } else if (os.contains("linux")) {
            return "-unknown-linux-gnu";
        } else if (os.startsWith("macosx") || os.startsWith("osx") || os.startsWith("darwin")) {
            return "-apple-darwin";
        } else {
            return os;
        }
    }

}
