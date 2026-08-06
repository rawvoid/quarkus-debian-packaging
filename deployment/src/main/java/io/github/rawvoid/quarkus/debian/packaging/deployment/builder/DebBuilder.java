package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

/**
 * Builds a binary {@code .deb} package (ar of debian-binary + control.tar.gz + data.tar.gz).
 */
public final class DebBuilder {

    private static final byte[] DEBIAN_BINARY = "2.0\n".getBytes(StandardCharsets.US_ASCII);

    private DebBuilder() {
    }

    public static void build(Path debFile, String controlText, List<DebEntry> controlEntries, List<DebEntry> dataEntries)
            throws IOException {
        Objects.requireNonNull(debFile, "debFile");
        Objects.requireNonNull(controlText, "controlText");
        Objects.requireNonNull(controlEntries, "controlEntries");
        Objects.requireNonNull(dataEntries, "dataEntries");

        Path parent = debFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        DataArchive dataArchive = buildDataArchive(dataEntries);
        byte[] controlTarGz = buildControlArchive(controlText, controlEntries, dataArchive);

        try (OutputStream fileOut = Files.newOutputStream(debFile);
                ArArchiveOutputStream ar = new ArArchiveOutputStream(fileOut)) {
            writeArEntry(ar, "debian-binary", DEBIAN_BINARY);
            writeArEntry(ar, "control.tar.gz", controlTarGz);
            writeArEntry(ar, "data.tar.gz", dataArchive.bytes());
        }
    }

    private static void writeArEntry(ArArchiveOutputStream ar, String name, byte[] content) throws IOException {
        ArArchiveEntry entry = new ArArchiveEntry(name, content.length);
        ar.putArchiveEntry(entry);
        ar.write(content);
        ar.closeArchiveEntry();
    }

    private static byte[] buildControlArchive(String controlText, List<DebEntry> controlEntries, DataArchive dataArchive)
            throws IOException {
        List<DebEntry> all = new ArrayList<>();
        all.add(DebEntry.bytes("control", controlText.getBytes(StandardCharsets.UTF_8), DebEntry.MODE_FILE, false));
        all.add(DebEntry.bytes("md5sums", dataArchive.md5sums().getBytes(StandardCharsets.UTF_8), DebEntry.MODE_FILE, false));
        if (!dataArchive.conffiles().isEmpty()) {
            StringBuilder conf = new StringBuilder();
            for (String path : dataArchive.conffiles()) {
                conf.append('/').append(path).append('\n');
            }
            all.add(DebEntry.bytes("conffiles", conf.toString().getBytes(StandardCharsets.UTF_8), DebEntry.MODE_FILE, false));
        }
        all.addAll(controlEntries);

        return writeTarGz(all, false);
    }

    private static DataArchive buildDataArchive(List<DebEntry> dataEntries) throws IOException {
        List<DebEntry> expanded = expandWithParentDirs(dataEntries);
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StringBuilder md5sums = new StringBuilder();
        Set<String> conffiles = new TreeSet<>();
        long installedBytes = 0;

        GzipParameters gzip = new GzipParameters();
        gzip.setOperatingSystem(3); // Unix
        try (GzipCompressorOutputStream gz = new GzipCompressorOutputStream(bos, gzip);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gz, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);

            for (DebEntry entry : expanded) {
                if (entry.directory()) {
                    writeDirectory(tar, entry);
                    continue;
                }

                long size;
                if (entry.content() != null) {
                    size = entry.content().length;
                    writeFile(tar, entry, entry.content());
                    md5.reset();
                    md5.update(entry.content());
                } else {
                    size = Files.size(entry.source());
                    md5.reset();
                    try (InputStream in = Files.newInputStream(entry.source());
                            DigestInputStream din = new DigestInputStream(in, md5)) {
                        writeFile(tar, entry, din, size);
                    }
                }
                installedBytes += size;
                String digest = HexFormat.of().formatHex(md5.digest());
                md5sums.append(digest).append("  ").append(entry.packagePath()).append('\n');
                if (entry.confFile()) {
                    conffiles.add(entry.packagePath());
                }
            }
            tar.finish();
        }

        return new DataArchive(bos.toByteArray(), md5sums.toString(), conffiles, installedBytes);
    }

    private static byte[] writeTarGz(List<DebEntry> entries, boolean includeParents) throws IOException {
        List<DebEntry> expanded = includeParents ? expandWithParentDirs(entries) : entries;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GzipParameters gzip = new GzipParameters();
        gzip.setOperatingSystem(3);
        try (GzipCompressorOutputStream gz = new GzipCompressorOutputStream(bos, gzip);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gz, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            for (DebEntry entry : expanded) {
                if (entry.directory()) {
                    writeDirectory(tar, entry);
                } else if (entry.content() != null) {
                    writeFile(tar, entry, entry.content());
                } else {
                    try (InputStream in = Files.newInputStream(entry.source())) {
                        writeFile(tar, entry, in, Files.size(entry.source()));
                    }
                }
            }
            tar.finish();
        }
        return bos.toByteArray();
    }

    private static List<DebEntry> expandWithParentDirs(List<DebEntry> entries) {
        Set<String> dirs = new LinkedHashSet<>();
        List<DebEntry> files = new ArrayList<>();
        for (DebEntry entry : entries) {
            addParentDirs(entry.packagePath(), dirs);
            if (entry.directory()) {
                dirs.add(entry.packagePath());
            } else {
                files.add(entry);
            }
        }
        List<DebEntry> result = new ArrayList<>(dirs.size() + files.size());
        dirs.stream()
                .sorted(Comparator.comparingInt((String s) -> s.split("/").length).thenComparing(s -> s))
                .forEach(dir -> result.add(DebEntry.directory(dir)));
        files.stream()
                .sorted(Comparator.comparing(DebEntry::packagePath))
                .forEach(result::add);
        return result;
    }

    private static void addParentDirs(String packagePath, Set<String> dirs) {
        int idx = packagePath.indexOf('/');
        while (idx > 0) {
            dirs.add(packagePath.substring(0, idx));
            idx = packagePath.indexOf('/', idx + 1);
        }
    }

    private static void writeDirectory(TarArchiveOutputStream tar, DebEntry entry) throws IOException {
        String name = entry.packagePath().endsWith("/") ? entry.packagePath() : entry.packagePath() + "/";
        TarArchiveEntry tarEntry = new TarArchiveEntry(name);
        tarEntry.setMode(entry.mode());
        tarEntry.setUserId(0);
        tarEntry.setGroupId(0);
        tarEntry.setModTime(0L);
        tar.putArchiveEntry(tarEntry);
        tar.closeArchiveEntry();
    }

    private static void writeFile(TarArchiveOutputStream tar, DebEntry entry, byte[] content) throws IOException {
        TarArchiveEntry tarEntry = new TarArchiveEntry(entry.packagePath());
        tarEntry.setSize(content.length);
        tarEntry.setMode(entry.mode());
        tarEntry.setUserId(0);
        tarEntry.setGroupId(0);
        tarEntry.setModTime(0L);
        tar.putArchiveEntry(tarEntry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    private static void writeFile(TarArchiveOutputStream tar, DebEntry entry, InputStream in, long size) throws IOException {
        TarArchiveEntry tarEntry = new TarArchiveEntry(entry.packagePath());
        tarEntry.setSize(size);
        tarEntry.setMode(entry.mode());
        tarEntry.setUserId(0);
        tarEntry.setGroupId(0);
        tarEntry.setModTime(0L);
        tar.putArchiveEntry(tarEntry);
        in.transferTo(tar);
        tar.closeArchiveEntry();
    }

    /**
     * Formats the Installed-Size field (KiB, rounded up).
     */
    public static long installedSizeKiB(long installedBytes) {
        return Math.max(1L, (installedBytes + 1023) / 1024);
    }

    private record DataArchive(byte[] bytes, String md5sums, Set<String> conffiles, long installedBytes) {
    }
}
