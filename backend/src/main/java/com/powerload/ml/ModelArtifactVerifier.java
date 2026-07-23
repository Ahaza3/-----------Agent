package com.powerload.ml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates immutable model artifact directories shared with the Flask service. */
public final class ModelArtifactVerifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ModelArtifactVerifier() {
    }

    public static ArtifactManifest verify(Path modelRoot, String artifactDir) {
        Path root = modelRoot.toAbsolutePath().normalize();
        if (artifactDir == null || !artifactDir.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("PATH_VALIDATION: invalid artifact directory");
        }
        Path directory = root.resolve(artifactDir).normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("PATH_VALIDATION: artifact directory is unavailable");
        }
        try {
            Path manifestPath = directory.resolve("manifest.json");
            if (!Files.isRegularFile(manifestPath)) {
                throw new IllegalArgumentException("MANIFEST_READ: manifest.json is missing");
            }
            Map<String, Object> source = OBJECT_MAPPER.readValue(manifestPath.toFile(), new TypeReference<>() { });
            String version = stringValue(source.get("modelVersion"));
            String checksum = stringValue(source.get("artifactChecksum"));
            if (!artifactDir.equals(version) || checksum.isBlank()) {
                throw new IllegalArgumentException("MANIFEST_READ: invalid manifest identity");
            }
            Object filesValue = source.get("files");
            if (!(filesValue instanceof List<?> files) || files.isEmpty()) {
                throw new IllegalArgumentException("MANIFEST_READ: manifest files are missing");
            }
            List<FileChecksum> entries = new ArrayList<>();
            for (Object file : files) {
                if (!(file instanceof Map<?, ?> values)) {
                    throw new IllegalArgumentException("MANIFEST_READ: invalid file entry");
                }
                String path = stringValue(values.get("path"));
                String sha256 = stringValue(values.get("sha256"));
                if (!path.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,255}") || path.contains("..")
                        || sha256.length() != 64) {
                    throw new IllegalArgumentException("MANIFEST_READ: invalid file entry");
                }
                Path filePath = directory.resolve(path).normalize();
                if (!filePath.startsWith(directory) || !Files.isRegularFile(filePath)) {
                    throw new IllegalArgumentException("CHECKSUM_VERIFY: required artifact file is missing");
                }
                String actual = sha256(filePath);
                if (!actual.equalsIgnoreCase(sha256)) {
                    throw new IllegalArgumentException("CHECKSUM_VERIFY: file checksum mismatch");
                }
                entries.add(new FileChecksum(path, actual));
            }
            entries.sort(Comparator.comparing(FileChecksum::path));
            List<String> actualPaths;
            try (var paths = Files.walk(directory)) {
                actualPaths = paths.filter(Files::isRegularFile)
                        .map(path -> directory.relativize(path).toString().replace('\\', '/'))
                        .filter(path -> !"manifest.json".equals(path))
                        .sorted()
                        .toList();
            }
            if (!actualPaths.equals(entries.stream().map(FileChecksum::path).toList())) {
                throw new IllegalArgumentException("MANIFEST_READ: manifest does not cover all artifact files");
            }
            String actualChecksum = artifactChecksum(entries);
            if (!actualChecksum.equalsIgnoreCase(checksum)) {
                throw new IllegalArgumentException("CHECKSUM_VERIFY: artifact checksum mismatch");
            }
            return new ArtifactManifest(version, stringValue(source.get("modelType")), actualChecksum, directory, entries);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("MANIFEST_READ: cannot read manifest");
        }
    }

    public static String artifactChecksum(List<FileChecksum> files) {
        StringBuilder content = new StringBuilder();
        files.stream().sorted(Comparator.comparing(FileChecksum::path)).forEach(file -> content
                .append(file.path()).append('\n').append(file.sha256()).append('\n'));
        return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new java.io.OutputStream() {
                @Override public void write(int b) { digest.update((byte) b); }
                @Override public void write(byte[] b, int off, int len) { digest.update(b, off, len); }
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record FileChecksum(String path, String sha256) { }
    public record ArtifactManifest(String modelVersion, String modelType, String artifactChecksum,
                                   Path directory, List<FileChecksum> files) { }
}
