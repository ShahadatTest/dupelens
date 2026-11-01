package dev.dupelens;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Read-only duplicate discovery. Symbolic links are never followed. */
public final class Main {
    public record Group(long size, List<Path> files) {
        public long reclaimable() { return size * (files.size() - 1); }
    }
    public record Scan(List<Group> groups, List<String> warnings, long files) {}

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help")) {
            System.out.println("DupeLens — find duplicate files, change nothing.\nUsage: java -jar dupelens.jar <directory> [--html <report.html>]");
            return;
        }
        try {
            if (args.length != 1 && !(args.length == 3 && args[1].equals("--html")))
                throw new IllegalArgumentException("Expected <directory> [--html <report.html>]");
            Scan scan = scan(Path.of(args[0]));
            long bytes = scan.groups.stream().mapToLong(Group::reclaimable).sum();
            System.out.printf("Scanned %d files | %d duplicate groups | %,d redundant bytes%n", scan.files, scan.groups.size(), bytes);
            for (Group group : scan.groups) {
                System.out.printf("%n%,d bytes each (%d copies)%n", group.size, group.files.size());
                group.files.forEach(p -> System.out.println("  " + p));
            }
            scan.warnings.forEach(w -> System.err.println("Warning: " + w));
            if (args.length == 3) {
                Path report = Path.of(args[2]).toAbsolutePath();
                // Never replace an existing file, even if it happens to be an input.
                Files.writeString(report, html(scan), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                System.out.println("\nReport: " + report);
            }
        } catch (Exception e) {
            System.err.println("DupeLens: " + e.getMessage());
            System.exit(1);
        }
    }

    public static Scan scan(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Not a directory: " + root);
        Map<Long, List<Path>> sizes = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        Set<Object> identities = new HashSet<>();
        long[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    count[0]++;
                    // Hard links already share storage; don't count them twice where file keys exist.
                    if (attrs.fileKey() != null && !identities.add(attrs.fileKey())) return FileVisitResult.CONTINUE;
                    sizes.computeIfAbsent(attrs.size(), ignored -> new ArrayList<>()).add(path);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException error) {
                warnings.add(path + ": " + error.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        List<Group> groups = new ArrayList<>();
        for (var bucket : sizes.entrySet()) {
            if (bucket.getValue().size() < 2) continue;
            Map<String, List<Path>> hashes = new TreeMap<>();
            for (Path path : bucket.getValue()) {
                try {
                    BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!before.isRegularFile() || before.size() != bucket.getKey()) throw new IOException("file changed during scan");
                    String hash = digest(path);
                    BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                            || !Objects.equals(before.fileKey(), after.fileKey())) throw new IOException("file changed during scan");
                    // Windows providers may return null file keys. Check actual file identity
                    // within matching-content buckets before counting another physical copy.
                    List<Path> matches = hashes.computeIfAbsent(hash, ignored -> new ArrayList<>());
                    boolean alias = false;
                    for (Path existing : matches) {
                        if (Files.isSameFile(existing, path)) { alias = true; break; }
                    }
                    if (!alias) matches.add(path);
                } catch (IOException e) { warnings.add(path + ": " + e.getMessage()); }
            }
            for (List<Path> paths : hashes.values()) if (paths.size() > 1) {
                paths.sort(Comparator.comparing(Path::toString));
                groups.add(new Group(bucket.getKey(), List.copyOf(paths)));
            }
        }
        groups.sort(Comparator.comparingLong(Group::reclaimable).reversed().thenComparing(g -> g.files.get(0).toString()));
        return new Scan(List.copyOf(groups), List.copyOf(warnings), count[0]);
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) md.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String html(Scan scan) {
        long bytes = scan.groups.stream().mapToLong(Group::reclaimable).sum();
        StringBuilder out = new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>DupeLens report</title><style>body{font:16px system-ui;background:#0b1020;color:#e5ecff;max-width:1000px;margin:60px auto;padding:24px}h1{font-size:48px;letter-spacing:-2px}h1 span{color:#6ee7b7}article{background:#151e34;border:1px solid #283752;border-radius:16px;padding:24px;margin:20px 0}li{overflow-wrap:anywhere;margin:12px 0}small{color:#a8b7d4}.stats{font-size:24px;color:#6ee7b7}</style><h1>Dupe<span>Lens</span></h1><p>Find the copies. Keep control.</p>");
        out.append("<p class=\"stats\">").append(scan.groups.size()).append(" duplicate groups · ").append(String.format(Locale.ROOT, "%,d", bytes)).append(" redundant bytes</p><small>").append(scan.files).append(" files scanned. No files modified. Review paths before taking action.</small>");
        for (Group group : scan.groups) {
            out.append("<article><h2>").append(group.size).append(" bytes × ").append(group.files.size()).append(" copies</h2><ul>");
            group.files.forEach(p -> out.append("<li>").append(escape(p.toString())).append("</li>"));
            out.append("</ul></article>");
        }
        if (scan.groups.isEmpty()) out.append("<article>No duplicates found.</article>");
        if (!scan.warnings.isEmpty()) {
            out.append("<h2>Warnings</h2><ul>");
            scan.warnings.forEach(w -> out.append("<li>").append(escape(w)).append("</li>"));
            out.append("</ul>");
        }
        return out.append("</html>").toString();
    }
}
