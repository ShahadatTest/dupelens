package dev.dupelens;
import java.nio.file.*;
import java.util.Comparator;

/** Dependency-free integration checks; run with scripts/test.ps1 or scripts/test.sh. */
public final class SmokeTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dupelens-test");
        try {
            Files.writeString(root.resolve("one.txt"), "same");
            Files.writeString(root.resolve("two.txt"), "same");
            Files.writeString(root.resolve("different.txt"), "diff");
            Files.createFile(root.resolve("empty1"));
            Files.createFile(root.resolve("empty2"));
            var scan = Main.scan(root);
            check(scan.groups().size() == 2, "same-size different contents must not match");
            check(scan.groups().get(0).reclaimable() == 4, "reclaimable bytes");
            check(scan.files() == 5, "file count");
            check(Files.readString(root.resolve("one.txt")).equals("same"), "source preserved");
            String html = Main.html(new Main.Scan(java.util.List.of(), java.util.List.of("<script> & \""), 0));
            check(!html.contains("<script>"), "HTML escaping");
            check(html.contains("&lt;script&gt;"), "escaped warning");
            check(html.contains("No duplicates found."), "empty report");
            String report = Main.html(new Main.Scan(java.util.List.of(
                new Main.Group(1887436, java.util.List.of(Path.of("hero&.png"), Path.of("backup.png")))),
                java.util.List.of(), 12480));
            check(report.contains("<details open>"), "expandable group");
            check(report.contains("hero&amp;.png"), "escaped filename and path");
            check(report.contains("12,480"), "formatted scan count");
            check(report.contains("1.8 MiB"), "readable redundant size uses IEC labels");
            check(report.contains("2 files"), "group file count");
            check(!report.contains("<script") && !report.contains("src=\"http"), "offline report");
            String kib = Main.html(new Main.Scan(java.util.List.of(
                new Main.Group(1024, java.util.List.of(Path.of("a.txt"), Path.of("b.txt")))),
                java.util.List.of(), 2));
            check(kib.contains("1.0 KiB"), "1024 bytes labelled KiB");
            String bytes = Main.html(new Main.Scan(java.util.List.of(
                new Main.Group(512, java.util.List.of(Path.of("c.txt"), Path.of("d.txt")))),
                java.util.List.of(), 2));
            check(bytes.contains("512 B"), "sizes below 1024 stay in bytes");
            try { Main.scan(root.resolve("missing")); throw new AssertionError("missing directory accepted"); }
            catch (java.io.IOException expected) { }
            try {
                Files.createLink(root.resolve("hardlink"), root.resolve("one.txt"));
                check(Main.scan(root).groups().get(0).files().size() == 2, "hard links counted twice");
            } catch (UnsupportedOperationException | java.io.IOException ignored) { }
            System.out.println("All integration checks passed.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
