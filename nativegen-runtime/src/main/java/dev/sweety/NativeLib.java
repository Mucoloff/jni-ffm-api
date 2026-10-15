package dev.sweety;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class NativeLib {
    private static final Set<Backend> loaded = EnumSet.noneOf(Backend.class);
    private static final Map<Backend, Path> resolvedPaths = new EnumMap<>(Backend.class);
    private static final Map<Backend, Boolean> availability = new EnumMap<>(Backend.class);

    private NativeLib() {
    }

    /**
     * Checks if the given backend's native library is available on this system.
     * Respects {@code -Dsweety.natives.disabled=true} to force pure-Java fallback.
     */
    public static synchronized boolean isAvailable(Backend backend) {
        if (Boolean.getBoolean("sweety.natives.disabled")) {
            return false;
        }
        Boolean cached = availability.get(backend);
        if (cached != null) {
            return cached;
        }
        try {
            Path path = libraryPath(backend);
            boolean ok = path != null && Files.isRegularFile(path);
            availability.put(backend, ok);
            return ok;
        } catch (Throwable t) {
            availability.put(backend, false);
            return false;
        }
    }

    /**
     * Checks if the default Rust native backend is available.
     */
    public static boolean isAvailable() {
        return isAvailable(Backend.RUST);
    }

    /**
     * Load a backend's library into the JVM for JNI use (idempotent per backend).
     */
    public static synchronized void loadForJni(Backend backend) {
        if (loaded.contains(backend)) return;
        Path path = libraryPath(backend);
        System.load(path.toAbsolutePath().toString());
        loaded.add(backend);
    }

    /**
     * Resolve the on-disk path of a backend's shared library.
     * <ol>
     *   <li>Explicit path from {@code sweety.natives.dir} or {@code SWEETY_NATIVES_DIR}</li>
     *   <li>Directories listed in {@code java.library.path}</li>
     *   <li>Common workspace dev paths (target/release, build/natives)</li>
     *   <li>Embedded JAR resource under {@code /natives/{os}-{arch}/{libName}}</li>
     * </ol>
     */
    public static synchronized Path libraryPath(Backend backend) {
        Path existing = resolvedPaths.get(backend);
        if (existing != null && Files.isRegularFile(existing)) {
            return existing;
        }

        String fileName = System.mapLibraryName(backend.libName());

        // 1. Explicit directory via property or environment
        String explicitDir = System.getProperty("sweety.natives.dir");
        if (explicitDir == null || explicitDir.isBlank()) {
            explicitDir = System.getenv("SWEETY_NATIVES_DIR");
        }
        if (explicitDir != null && !explicitDir.isBlank()) {
            Path p = Paths.get(explicitDir, fileName);
            if (Files.isRegularFile(p)) {
                resolvedPaths.put(backend, p);
                return p;
            }
        }

        // 2. java.library.path scanning
        for (String dir : System.getProperty("java.library.path", "").split(File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            Path p = Paths.get(dir, fileName);
            if (Files.isRegularFile(p)) {
                resolvedPaths.put(backend, p);
                return p;
            }
        }

        // 3. Local dev paths (Cargo & Gradle build directories)
        String[] devPaths = new String[]{
                "native/rust/target/release/" + fileName,
                "../sweety-native/native/rust/target/release/" + fileName,
                "../../sweety-native/native/rust/target/release/" + fileName,
                "build/natives/" + fileName,
                "../sweety-native/build/natives/" + fileName
        };
        for (String devPath : devPaths) {
            Path p = Paths.get(devPath);
            if (Files.isRegularFile(p)) {
                resolvedPaths.put(backend, p);
                return p;
            }
        }

        // 4. Embedded JAR resource extraction
        Path unpacked = unpackResource(backend, fileName);
        if (unpacked != null && Files.isRegularFile(unpacked)) {
            resolvedPaths.put(backend, unpacked);
            return unpacked;
        }

        throw new IllegalStateException("Native library " + fileName + " not found on system paths or resources");
    }

    private static Path unpackResource(Backend backend, String fileName) {
        String os = normalizeOs();
        String arch = normalizeArch();
        String resourcePath = "/natives/" + os + "-" + arch + "/" + fileName;

        try (InputStream in = NativeLib.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path cacheDir = Paths.get(System.getProperty("user.home"), ".sweety", "natives", os + "-" + arch);
            Files.createDirectories(cacheDir);
            Path targetFile = cacheDir.resolve(fileName);

            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            targetFile.toFile().deleteOnExit();
            return targetFile;
        } catch (IOException e) {
            return null;
        }
    }

    private static String normalizeOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("linux")) return "linux";
        if (os.contains("win")) return "windows";
        return os;
    }

    private static String normalizeArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        if (arch.equals("amd64") || arch.equals("x86_64")) return "x86_64";
        return arch;
    }
}
