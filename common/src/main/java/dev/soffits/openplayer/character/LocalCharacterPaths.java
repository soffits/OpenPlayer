package dev.soffits.openplayer.character;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class LocalCharacterPaths {
    private LocalCharacterPaths() {
    }

    static Path safeChild(Path parent, String fileName) throws IOException {
        if (parent == null) {
            throw new IOException("Directory is unavailable");
        }
        LocalCharacterFileOperationResult fileNameValidation = LocalCharacterRepository.validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            throw new UnsafeCharacterPathException("Unsafe file name");
        }
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path child = normalizedParent.resolve(fileName).normalize();
        if (!child.startsWith(normalizedParent)) {
            throw new UnsafeCharacterPathException("File escapes directory");
        }
        return child;
    }

    static void ensureReadableDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Directory is unavailable");
        }
        rejectSymbolicLinkPath(path);
        if (Files.isSymbolicLink(path)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory is unavailable");
        }
    }

    static void ensureWritableDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Directory is unavailable");
        }
        rejectSymbolicLinkPath(path);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path)) {
                throw new UnsafeCharacterPathException("Symbolic links are not allowed");
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Directory is unavailable");
            }
            return;
        }
        Files.createDirectories(path);
        rejectSymbolicLinkPath(path);
        if (Files.isSymbolicLink(path)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory is unavailable");
        }
    }

    static void rejectSymbolicLinkTarget(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
    }

    private static void rejectSymbolicLinkPath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path current = absolutePath.getRoot();
        for (Path name : absolutePath) {
            current = current == null ? name : current.resolve(name);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new UnsafeCharacterPathException("Symbolic links are not allowed");
            }
        }
    }
}
