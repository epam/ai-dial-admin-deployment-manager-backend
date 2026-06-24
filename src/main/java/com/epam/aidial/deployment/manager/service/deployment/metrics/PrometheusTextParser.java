package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.ParsedExposition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-rolled parser for the Prometheus text exposition format 0.0.4 (the format every target
 * engine serves on {@code /metrics}). Nothing on the classpath parses this format — Micrometer
 * and the OTel SDK only produce it — and adding a dependency for a stable ~100-line grammar is
 * not justified (spike §5 explicit non-change).
 *
 * <p>Supported: {@code name{labels} value [timestamp]} lines, escaped label values
 * ({@code \\}, {@code \"}, {@code \n}), {@code +Inf}/{@code -Inf}/{@code NaN}/scientific-notation
 * values. {@code # HELP} comments and OpenMetrics exemplars are ignored; {@code # TYPE} comments
 * are not value-bearing but their declared metric name is retained in
 * {@link ParsedExposition#declaredMetricNames()} so engine detection can recognize label-gated
 * histograms that emit no samples while idle. Unparseable lines are skipped with a debug log,
 * never propagated as errors.</p>
 */
@Slf4j
@Component
@LogExecution
public class PrometheusTextParser {

    private static final String TYPE_COMMENT_PREFIX = "# TYPE ";

    public ParsedExposition parse(String text) {
        var samples = new ArrayList<MetricSample>();
        var declaredMetricNames = new HashSet<String>();
        if (StringUtils.isBlank(text)) {
            return new ParsedExposition(samples, declaredMetricNames);
        }
        for (var line : text.split("\n")) {
            var trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                declaredNameFromTypeComment(trimmed).ifPresent(declaredMetricNames::add);
                continue;
            }
            try {
                var sample = parseLine(trimmed);
                if (sample != null) {
                    samples.add(sample);
                }
            } catch (NumberFormatException | IllegalStateException e) {
                log.debug("Skipping unparseable exposition line '{}': {}", trimmed, e.getMessage());
            }
        }
        return new ParsedExposition(samples, declaredMetricNames);
    }

    /** Extracts the metric base name from a {@code # TYPE <name> <type>} comment line. */
    private static Optional<String> declaredNameFromTypeComment(String comment) {
        if (!comment.startsWith(TYPE_COMMENT_PREFIX)) {
            return Optional.empty();
        }
        var rest = comment.substring(TYPE_COMMENT_PREFIX.length()).trim();
        int spaceIdx = rest.indexOf(' ');
        var name = spaceIdx < 0 ? rest : rest.substring(0, spaceIdx);
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private MetricSample parseLine(String line) {
        String name;
        Map<String, String> labels;
        String rest;

        int braceIdx = line.indexOf('{');
        int spaceIdx = indexOfWhitespace(line, 0);
        if (braceIdx >= 0 && (spaceIdx < 0 || braceIdx < spaceIdx)) {
            name = line.substring(0, braceIdx);
            int closeIdx = findClosingBrace(line, braceIdx);
            labels = parseLabels(line.substring(braceIdx + 1, closeIdx));
            rest = line.substring(closeIdx + 1).trim();
        } else {
            if (spaceIdx < 0) {
                return null;
            }
            name = line.substring(0, spaceIdx);
            labels = Map.of();
            rest = line.substring(spaceIdx).trim();
        }

        // OpenMetrics exemplars trail after " # "; classic 0.0.4 has none — strip defensively.
        int exemplarIdx = rest.indexOf(" # ");
        if (exemplarIdx >= 0) {
            rest = rest.substring(0, exemplarIdx).trim();
        }
        if (rest.isEmpty()) {
            return null;
        }
        // Value is the first token; an optional timestamp may follow and is ignored.
        int valueEnd = indexOfWhitespace(rest, 0);
        var valueToken = valueEnd < 0 ? rest : rest.substring(0, valueEnd);
        return new MetricSample(name, labels, parseValue(valueToken));
    }

    private static double parseValue(String token) {
        return switch (token) {
            case "+Inf", "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NaN", "+NaN", "-NaN" -> Double.NaN;
            default -> Double.parseDouble(token);
        };
    }

    private static Map<String, String> parseLabels(String inner) {
        var labels = new HashMap<String, String>();
        int i = 0;
        int length = inner.length();
        while (i < length) {
            while (i < length && (inner.charAt(i) == ',' || Character.isWhitespace(inner.charAt(i)))) {
                i++;
            }
            if (i >= length) {
                break;
            }
            int eqIdx = inner.indexOf('=', i);
            if (eqIdx < 0) {
                break;
            }
            var key = inner.substring(i, eqIdx).trim();
            int quoteStart = inner.indexOf('"', eqIdx);
            if (quoteStart < 0) {
                break;
            }
            var value = new StringBuilder();
            int j = quoteStart + 1;
            while (j < length && inner.charAt(j) != '"') {
                char c = inner.charAt(j);
                if (c == '\\' && j + 1 < length) {
                    j++;
                    char escaped = inner.charAt(j);
                    value.append(switch (escaped) {
                        case 'n' -> '\n';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> escaped;
                    });
                } else {
                    value.append(c);
                }
                j++;
            }
            labels.put(key, value.toString());
            i = j + 1;
        }
        return labels;
    }

    private static int findClosingBrace(String line, int openIdx) {
        boolean inQuotes = false;
        for (int i = openIdx + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inQuotes) {
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '}' && !inQuotes) {
                return i;
            }
        }
        throw new IllegalStateException("Unbalanced '{' in exposition line");
    }

    private static int indexOfWhitespace(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

}
