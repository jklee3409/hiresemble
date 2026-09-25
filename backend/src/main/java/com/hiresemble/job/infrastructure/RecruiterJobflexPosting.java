package com.hiresemble.job.infrastructure;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recruiter(jobflex) career pages render the posting body client-side, so the static HTML has no
 * posting text or images. The same public posting is served by a fixed JSON endpoint that is
 * selected by the tenant host in the {@code prefix} header. This class only maps an already
 * fetched tenant page URL to that endpoint and converts the JSON into sanitized posting HTML; all
 * network access stays in {@link SecureJobPageFetchAdapter}.
 */
final class RecruiterJobflexPosting {

    static final URI API_BASE = URI.create("https://api-recruiter.recruiter.co.kr/position/v2/jobflex/");

    private static final Pattern TENANT_HOST = Pattern.compile(
            "(?!api-)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.recruiter\\.co\\.kr");
    private static final Pattern POSITION_PATH = Pattern.compile("/career/jobs/(\\d{1,12})/?");
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final int MAX_FIELD_CHARACTERS = 500;
    private static final int MAX_TAGS = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RecruiterJobflexPosting() {}

    static Optional<ApiRequest> apiRequest(URI pageUri) {
        if (pageUri == null
                || !"https".equalsIgnoreCase(pageUri.getScheme())
                || pageUri.getHost() == null
                || pageUri.getPort() != -1
                || pageUri.getRawUserInfo() != null) {
            return Optional.empty();
        }
        String host = pageUri.getHost().toLowerCase(Locale.ROOT);
        Matcher path = POSITION_PATH.matcher(pageUri.getRawPath() == null ? "" : pageUri.getRawPath());
        if (!TENANT_HOST.matcher(host).matches() || !path.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ApiRequest(
                API_BASE.resolve(path.group(1)),
                Map.of("Accept", "application/json", "prefix", host)));
    }

    /** Returns sanitized posting HTML, or empty when the JSON carries no usable posting content. */
    static Optional<String> postingHtml(String json, URI pageUri) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String title = text(root, "title");
        String description = rawText(root, "jobDescription");
        if (title == null && description == null) {
            return Optional.empty();
        }
        Document document = Document.createShell(pageUri.toASCIIString());
        if (title != null) {
            document.title(title);
        }
        Element main = document.body().appendElement("main");
        if (title != null) {
            main.appendElement("h1").text(title);
        }
        Element facts = main.appendElement("ul");
        addFact(facts, "채용 구분", text(root, "classificationCode"));
        addFact(facts, "신입/경력", careerType(text(root, "careerType")));
        addFact(facts, "접수 시작", dateTime(text(root, "startDateTime")));
        addFact(facts, "접수 마감", dateTime(text(root, "endDateTime")));
        addFact(facts, "태그", tags(root.path("tagList")));
        if (facts.childrenSize() == 0) {
            facts.remove();
        }
        if (description != null) {
            Element body = main.appendElement("section");
            if ("HTML".equalsIgnoreCase(text(root, "jobDescriptionType"))) {
                body.append(Jsoup.clean(description, pageUri.toASCIIString(), Safelist.relaxed()));
            } else {
                description.lines()
                        .map(String::strip)
                        .filter(line -> !line.isBlank())
                        .forEach(line -> body.appendElement("p").text(line));
            }
        }
        return Optional.of(document.outerHtml());
    }

    private static void addFact(Element list, String label, String value) {
        if (value != null) {
            list.appendElement("li").text(label + ": " + value);
        }
    }

    private static String careerType(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "NEW" -> "신입";
            case "CAREER", "EXPERIENCED" -> "경력";
            case "NEW_OR_CAREER", "ANY", "IRRELEVANT" -> "신입/경력";
            default -> value;
        };
    }

    private static String dateTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value).format(DISPLAY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private static String tags(JsonNode values) {
        if (values == null || !values.isArray()) return null;
        List<String> names = new ArrayList<>();
        for (JsonNode value : values) {
            String name = text(value, "tagName");
            if (name != null && names.size() < MAX_TAGS) names.add(name);
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private static String text(JsonNode node, String field) {
        String value = rawText(node, field);
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty()) return null;
        return normalized.length() > MAX_FIELD_CHARACTERS
                ? normalized.substring(0, MAX_FIELD_CHARACTERS)
                : normalized;
    }

    private static String rawText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString()) return null;
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    record ApiRequest(URI uri, Map<String, String> headers) {}
}
