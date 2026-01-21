package com.epam.aidial.deployment.manager.web.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@UtilityClass
public class CommandLineUtils {

    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";

    /**
     * Wraps the given string in quotes if it contains spaces or quote characters.
     *
     * <p>This method automatically selects the appropriate quote style (single or double)
     * to avoid escaping characters. If the string contains double quotes, single quotes
     * are used. In all other cases where quoting is required, double quotes are used.</p>
     *
     * @param argument the string to process.
     * @return the raw string if no quotes are needed, or the quoted string.
     * @throws IllegalArgumentException if the string contains both single and double quotes,
     *     making it impossible to wrap cleanly.
     * @implNote Copied from {@code org.apache.commons.exec.util.StringUtils#quoteArgument}.
     */
    public static String quoteArgument(final String argument) {

        String cleanedArgument = argument.trim();

        // strip the quotes from both ends
        while (cleanedArgument.startsWith(SINGLE_QUOTE) || cleanedArgument.startsWith(DOUBLE_QUOTE)) {
            cleanedArgument = cleanedArgument.substring(1);
        }

        while (cleanedArgument.endsWith(SINGLE_QUOTE) || cleanedArgument.endsWith(DOUBLE_QUOTE)) {
            cleanedArgument = cleanedArgument.substring(0, cleanedArgument.length() - 1);
        }

        final StringBuilder buf = new StringBuilder();
        if (cleanedArgument.contains(DOUBLE_QUOTE)) {
            if (cleanedArgument.contains(SINGLE_QUOTE)) {
                throw new IllegalArgumentException("Can't handle single and double quotes in same argument");
            }
            return buf.append(SINGLE_QUOTE).append(cleanedArgument).append(SINGLE_QUOTE).toString();
        }
        if (cleanedArgument.contains(SINGLE_QUOTE) || cleanedArgument.contains(" ")) {
            return buf.append(DOUBLE_QUOTE).append(cleanedArgument).append(DOUBLE_QUOTE).toString();
        }
        return cleanedArgument;
    }

    /**
     * @implNote Copied from {@code org.apache.commons.exec.CommandLine#translateCommandline(String)}.
     */
    public static List<String> parseCommandline(final String toProcess) {
        if (StringUtils.isBlank(toProcess)) {
            return null;
        }

        // parse with a simple finite state machine.
        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"' ", true);
        final ArrayList<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            final String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    switch (nextTok) {
                        case "'" -> state = inQuote;
                        case "\"" -> state = inDoubleQuote;
                        case " " -> {
                            if (lastTokenHasBeenQuoted || !current.isEmpty()) {
                                list.add(current.toString());
                                current = new StringBuilder();
                            }
                        }
                        case null, default -> current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }

        if (lastTokenHasBeenQuoted || !current.isEmpty()) {
            list.add(current.toString());
        }

        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("Unbalanced quotes in " + toProcess);
        }

        return list;
    }

}
