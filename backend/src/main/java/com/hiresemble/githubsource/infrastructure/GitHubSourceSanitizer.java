package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawFile;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawRepository;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedRepository;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedUnit;
import com.hiresemble.githubsource.application.GitHubSourceSanitizerPort;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class GitHubSourceSanitizer implements GitHubSourceSanitizerPort {

    private static final Set<String> BLOCKED_SEGMENTS = Set.of(
            "node_modules", "vendor", "dist", "build", "target", ".gradle", ".idea",
            ".cache", "cache", "coverage", "generated", "__pycache__", ".next", ".nuxt");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "tar",
            "7z", "rar", "jar", "war", "class", "exe", "dll", "so", "dylib", "mp3",
            "mp4", "mov", "avi", "woff", "woff2", "ttf", "eot", "db", "sqlite", "bin");
    private static final Set<String> MANIFEST_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "package.json", "pyproject.toml", "requirements.txt",
            "go.mod", "cargo.toml", "gemfile", "composer.json");
    private static final Pattern SECRET_FILE = Pattern.compile(
            "(?i)(^|/)([.]env(?:[.].*)?|id_(?:rsa|dsa|ecdsa|ed25519)|credentials?|secrets?|tokens?|.*[.](?:pem|key|p12|pfx|crt|cer))$");
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile(
            "(?i)^(\\s*(?:export\\s+)?[A-Za-z0-9_.-]*(?:password|passwd|secret|token|api[_-]?key|private[_-]?key)[A-Za-z0-9_.-]*\\s*[:=]\\s*).+$");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");

    private final GitHubProperties properties;

    public GitHubSourceSanitizer(GitHubProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<TreeEntry> selectCandidateFiles(List<TreeEntry> entries) {
        List<ScoredEntry> eligible = new ArrayList<>();
        for (TreeEntry entry : entries) {
            int score = score(entry);
            if (score >= 0) {
                eligible.add(new ScoredEntry(entry, score));
            }
        }
        eligible.sort(Comparator.comparingInt(ScoredEntry::score).reversed()
                .thenComparing(value -> value.entry().path(), String.CASE_INSENSITIVE_ORDER));
        return eligible.stream()
                .limit(properties.getMaxCandidateFiles())
                .map(ScoredEntry::entry)
                .toList();
    }

    @Override
    public SanitizedRepository sanitize(RawRepository repository) {
        List<SanitizedUnit> units = new ArrayList<>();
        String metadata = metadata(repository);
        units.add(unit("METADATA", ".hiresemble/repository-metadata", null, null, metadata));
        int codePoints = metadata.codePointCount(0, metadata.length());
        int excluded = 0;
        boolean complete = repository.fileFetchComplete()
                && !repository.upstreamTruncated()
                && repository.eligibleFileCount() <= properties.getMaxCandidateFiles();
        Set<String> paths = new HashSet<>();
        for (RawFile raw : repository.files()) {
            if (!paths.add(raw.entry().path())) {
                excluded++;
                complete = false;
                continue;
            }
            String decoded = decode(raw.content());
            if (decoded == null || decoded.indexOf('\0') >= 0) {
                excluded++;
                complete = false;
                continue;
            }
            String sanitized = sanitizeSecrets(decoded);
            if (sanitized.isBlank()) {
                excluded++;
                continue;
            }
            int remaining = properties.getMaxSanitizedCodePoints() - codePoints;
            if (remaining <= 0) {
                complete = false;
                excluded++;
                continue;
            }
            int count = sanitized.codePointCount(0, sanitized.length());
            if (count > remaining) {
                sanitized = firstCodePoints(sanitized, remaining);
                count = remaining;
                complete = false;
            }
            codePoints += count;
            units.add(unit(
                    unitType(raw.entry().path()),
                    raw.entry().path(),
                    raw.entry().sha(),
                    language(raw.entry().path()),
                    sanitized));
        }
        return new SanitizedRepository(
                repository.repository(),
                repository.commitSha(),
                repository.treeSha(),
                units,
                complete,
                repository.upstreamTruncated(),
                codePoints,
                excluded);
    }

    private int score(TreeEntry entry) {
        String path = entry.path().replace('\\', '/');
        String lower = path.toLowerCase(Locale.ROOT);
        if (!"blob".equals(entry.type())
                || "120000".equals(entry.mode())
                || "160000".equals(entry.mode())
                || entry.size() < 0
                || entry.size() > properties.getMaxTextFileBytes()
                || path.startsWith("/")
                || path.contains("\\")
                || path.codePoints().anyMatch(Character::isISOControl)
                || SECRET_FILE.matcher(lower).find()) {
            return -1;
        }
        String[] segments = lower.split("/");
        for (String segment : segments) {
            if (BLOCKED_SEGMENTS.contains(segment) || "..".equals(segment)) {
                return -1;
            }
        }
        String filename = segments[segments.length - 1];
        if (filename.endsWith(".lock")
                || filename.equals("package-lock.json")
                || filename.equals("yarn.lock")
                || filename.equals("pnpm-lock.yaml")
                || filename.matches(".*[.]min[.](js|css)$")) {
            return -1;
        }
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && BINARY_EXTENSIONS.contains(filename.substring(dot + 1))) {
            return -1;
        }
        if (filename.startsWith("readme")) return 1000;
        if (MANIFEST_NAMES.contains(filename)) return 900;
        if (filename.equals("dockerfile") || lower.endsWith("compose.yml")
                || lower.endsWith("compose.yaml") || filename.startsWith("docker-compose")) {
            return 850;
        }
        if (lower.startsWith(".github/workflows/")
                && (lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
            return 800;
        }
        if (lower.contains("/test/") || lower.contains("/tests/")
                || filename.contains("test") || filename.contains("spec")) {
            return 700;
        }
        if ((lower.contains("architecture") || lower.contains("design")
                        || lower.startsWith("docs/"))
                && isTextExtension(filename)) {
            return 650;
        }
        return isTextExtension(filename) ? 400 : -1;
    }

    private boolean isTextExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return filename.equals("makefile") || filename.equals("procfile");
        return Set.of(
                        "md", "mdx", "txt", "adoc", "rst", "java", "kt", "kts", "js", "jsx",
                        "ts", "tsx", "vue", "py", "go", "rs", "rb", "php", "cs", "c", "h",
                        "cpp", "hpp", "scala", "swift", "sql", "sh", "ps1", "yml", "yaml",
                        "json", "toml", "xml", "gradle", "properties", "conf", "ini", "html",
                        "css", "scss", "less")
                .contains(filename.substring(dot + 1));
    }

    private String metadata(RawRepository repository) {
        StringBuilder value = new StringBuilder();
        value.append("repository: ")
                .append(repository.repository().ownerLogin())
                .append('/')
                .append(repository.repository().repositoryName())
                .append('\n');
        if (repository.repository().description() != null) {
            value.append("description: ")
                    .append(repository.repository().description())
                    .append('\n');
        }
        value.append("defaultBranch: ").append(repository.repository().defaultBranch()).append('\n');
        if (!repository.repository().topics().isEmpty()) {
            value.append("topics: ").append(String.join(", ", repository.repository().topics())).append('\n');
        }
        if (!repository.languages().isEmpty()) {
            value.append("languages: ");
            repository.languages().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .forEach(entry -> value.append(entry.getKey())
                            .append('=')
                            .append(entry.getValue())
                            .append(' '));
        }
        return sanitizeSecrets(value.toString().strip());
    }

    private SanitizedUnit unit(
            String type, String path, String blobSha, String language, String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        String excerpt = normalized.replaceAll("\\s+", " ");
        if (excerpt.length() > 500) {
            excerpt = excerpt.substring(0, 500);
        }
        int lines = normalized.isEmpty() ? 0 : normalized.split("\n", -1).length;
        return new SanitizedUnit(
                type,
                path,
                blobSha,
                language,
                lines == 0 ? null : 1,
                lines == 0 ? null : lines,
                sha256(normalized),
                excerpt,
                normalized);
    }

    private String decode(byte[] bytes) {
        if (bytes.length > properties.getMaxTextFileBytes()) {
            return null;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private String sanitizeSecrets(String input) {
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean privateKey = false;
        for (String line : normalized.split("\n", -1)) {
            if (PRIVATE_KEY.matcher(line).find()) {
                privateKey = true;
                result.append("[MASKED_SECRET]\n");
                continue;
            }
            if (privateKey) {
                if (line.contains("-----END ") && line.contains("PRIVATE KEY-----")) {
                    privateKey = false;
                }
                continue;
            }
            result.append(ASSIGNMENT_SECRET.matcher(line).replaceFirst("$1[MASKED_SECRET]"))
                    .append('\n');
        }
        return result.toString().stripTrailing();
    }

    private String unitType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String filename = lower.substring(lower.lastIndexOf('/') + 1);
        if (filename.startsWith("readme")) return "README";
        if (MANIFEST_NAMES.contains(filename)) return "MANIFEST";
        if (filename.equals("dockerfile") || lower.contains("compose")) return "DOCKER";
        if (lower.startsWith(".github/workflows/")) return "CI";
        if (lower.contains("test") || lower.contains("spec")) return "TEST";
        if (lower.contains("architecture") || lower.contains("design") || lower.startsWith("docs/")) {
            return "ARCHITECTURE";
        }
        return "SOURCE";
    }

    private String language(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        return switch (filename.substring(dot + 1)) {
            case "java" -> "Java";
            case "kt", "kts" -> "Kotlin";
            case "js", "jsx" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "vue" -> "Vue";
            case "py" -> "Python";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "rb" -> "Ruby";
            case "php" -> "PHP";
            case "cs" -> "C#";
            case "c", "h" -> "C";
            case "cpp", "hpp" -> "C++";
            case "sql" -> "SQL";
            case "sh" -> "Shell";
            default -> null;
        };
    }

    private String firstCodePoints(String value, int count) {
        if (count <= 0) return "";
        int end = value.offsetByCodePoints(0, Math.min(count, value.codePointCount(0, value.length())));
        return value.substring(0, end);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ScoredEntry(TreeEntry entry, int score) {}
}
