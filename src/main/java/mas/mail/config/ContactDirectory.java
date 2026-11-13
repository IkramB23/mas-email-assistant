package mas.mail.config;

import mas.mail.model.Contact;
import mas.mail.util.Text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * address book ({@code data/contacts.csv}, separator ';')
 * format: {@code address;name;role;tags(comma separated);notes}
 * exact addresses win over patterns like {@code *@etu.univ-example.fr}
 */
public final class ContactDirectory {

    private final List<Contact> contacts;

    public ContactDirectory(List<Contact> contacts) {
        this.contacts = List.copyOf(contacts);
    }

    public static ContactDirectory load(Path csv) {
        List<Contact> list = new ArrayList<>();
        if (!Files.exists(csv)) {
            return new ContactDirectory(list);
        }
        try {
            for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(";", -1);
                if (f.length < 3) {
                    continue;
                }
                Set<String> tags = new HashSet<>();
                if (f.length > 3) {
                    for (String t : Text.splitList(f[3])) {
                        tags.add(Text.normalize(t));
                    }
                }
                list.add(new Contact(f[0].trim().toLowerCase(Locale.ROOT), f[1].trim(),
                        Text.normalize(f[2].trim()), tags, f.length > 4 ? f[4].trim() : ""));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ContactDirectory(list);
    }

    /**
     * finds the contact for an address (exact match first, then patterns)
     * never returns null
     */
    public Contact lookup(String address, String displayName) {
        String a = address.toLowerCase(Locale.ROOT);
        for (Contact c : contacts) {
            if (c.address().equals(a)) {
                return c;
            }
        }
        for (Contact c : contacts) {
            if (c.address().contains("*") && matchesGlob(a, c.address())) {
                return new Contact(a, displayName, c.role(), c.tags(), c.notes());
            }
        }
        return Contact.unknown(a, displayName);
    }

    public List<Contact> all() {
        return contacts;
    }

    /** simple pattern match where '*' means any text */
    public static boolean matchesGlob(String text, String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return text.toLowerCase(Locale.ROOT).matches(regex.toString());
    }
}
