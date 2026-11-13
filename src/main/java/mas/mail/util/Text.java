package mas.mail.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** small text helpers: normalise, split into words, estimate tokens, cut text, guess the language */
public final class Text {

    private static final Set<String> EN_STOP = Set.of(
            "the", "and", "for", "you", "your", "are", "was", "were", "this", "that", "with", "have", "has",
            "not", "but", "can", "could", "would", "will", "from", "about", "please", "thanks", "thank",
            "dear", "best", "regards", "our", "there", "their", "what", "which", "when", "also",
            "all", "any", "some", "into", "just", "like", "may", "might", "should", "been", "being", "let",
            "know", "hello", "kind", "who", "how", "its", "than", "then", "them", "they", "these", "those");

    private static final Set<String> FR_STOP = Set.of(
            "les", "des", "une", "est", "pour", "que", "qui", "dans", "sur", "pas", "par", "avec", "vous",
            "nous", "votre", "vos", "mais", "tout", "plus", "bonjour", "merci", "cordialement", "bien", "ces",
            "cette", "son", "ses", "aux", "elle", "ils", "leur", "comme", "fait", "etre", "avoir", "sont",
            "suis", "mon", "mes", "madame", "monsieur", "aussi", "donc", "car", "pouvez", "serait", "afin");

    private Text() {
    }

    /** puts text in lowercase and removes accents, so "Réunion" matches "reunion" */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** splits text into normalised words of 3 letters or more, without common English and French words */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        for (String w : normalize(s).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3 && !EN_STOP.contains(w) && !FR_STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** rough token count (about 4 characters per token), good enough to manage prompt size */
    public static int estimateTokens(String s) {
        return s == null ? 0 : (s.length() + 3) / 4;
    }

    public static String truncate(String s, int maxChars) {
        if (s == null || s.length() <= maxChars) {
            return s == null ? "" : s;
        }
        return s.substring(0, Math.max(0, maxChars - 15)).stripTrailing() + " [...truncated]";
    }

    /** very simple language guess based on common words: returns "fr", "en" or "unknown" */
    public static String guessLanguage(String s) {
        int en = 0;
        int fr = 0;
        for (String w : normalize(s).split("[^\\p{L}]+")) {
            if (EN_STOP.contains(w)) {
                en++;
            }
            if (FR_STOP.contains(w) || w.equals("le") || w.equals("la") || w.equals("je") || w.equals("et")
                    || w.equals("de") || w.equals("un") || w.equals("du")) {
                fr++;
            }
        }
        if (en == 0 && fr == 0) {
            return "unknown";
        }
        if (fr > en * 1.3) {
            return "fr";
        }
        if (en > fr * 1.3) {
            return "en";
        }
        return "unknown";
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String part : s.split("[,;\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
