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
