package mas.mail.mail;

import mas.mail.model.Email;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * reads simple text emails in RFC 822 style (headers, empty line, plain text body)
 * these are the .eml files that most mail apps export for plain text emails
 */
public final class EmailParser {

    private static final Pattern ADDRESS = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$");
    private static final DateTimeFormatter SIMPLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private EmailParser() {
    }

    public static Email parse(String raw, String fallbackId) {
        String text = raw.replace("\r\n", "\n");
        int sep = text.indexOf("\n\n");
        String headerPart = sep < 0 ? text : text.substring(0, sep);
        String body = sep < 0 ? "" : text.substring(sep + 2).strip();
        Map<String, String> headers = new HashMap<>();
        String lastKey = null;
        for (String line : headerPart.split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && lastKey != null) {
                headers.merge(lastKey, " " + line.strip(), String::concat);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                lastKey = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                headers.put(lastKey, line.substring(colon + 1).trim());
            }
        }
        String id = stripBrackets(headers.getOrDefault("message-id", ""));
        if (id.isBlank()) {
            id = fallbackId;
        }
        String[] from = parseAddress(headers.getOrDefault("from", ""));
        String inReplyTo = stripBrackets(headers.getOrDefault("in-reply-to", ""));
        List<String> refs = ids(headers.getOrDefault("references", ""));
        String threadId = !refs.isEmpty() ? refs.get(0) : (!inReplyTo.isBlank() ? inReplyTo : id);
        return new Email(id, from[1], from[0], addresses(headers.get("to")), addresses(headers.get("cc")),
                headers.getOrDefault("subject", ""), body, parseDate(headers.get("date")), threadId, inReplyTo,
                headers);
    }

    /** returns {name, address} */
    public static String[] parseAddress(String s) {
        Matcher m = ADDRESS.matcher(s);
        if (m.matches()) {
            return new String[]{m.group(1).trim(), m.group(2).trim().toLowerCase(Locale.ROOT)};
        }
        return new String[]{"", s.trim().toLowerCase(Locale.ROOT)};
    }

    private static List<String> addresses(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(",")) {
            String a = parseAddress(part)[1];
            if (!a.isBlank()) {
                out.add(a);
            }
        }
        return out;
    }

    private static List<String> ids(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<([^>]+)>").matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        if (out.isEmpty() && !s.isBlank()) {
            for (String p : s.trim().split("\\s+")) {
                out.add(p);
            }
        }
        return out;
    }

    private static String stripBrackets(String s) {
        return s.trim().replaceAll("^<|>$", "");
    }

    static LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return LocalDateTime.parse(s.trim());
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return LocalDateTime.parse(s.trim(), SIMPLE);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
