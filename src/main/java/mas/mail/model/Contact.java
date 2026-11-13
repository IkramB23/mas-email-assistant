package mas.mail.model;

import java.util.Set;

/**
 * a known person
 * the address can be a pattern (like {@code *@etu.univ-example.fr})
 * to describe a whole group (all students, all admin services...)
 *
 * @param address address or pattern
 * @param name    name shown to the user
 * @param role    role in the organisation (student, lecturer, administration, partner...)
 * @param tags    free tags (like the projects the person works on)
 * @param notes   free notes from the user, given to the agents as context about the sender
 */
public record Contact(String address, String name, String role, Set<String> tags, String notes) {

    public Contact {
        tags = Set.copyOf(tags);
        name = name == null ? "" : name;
        role = role == null ? "" : role;
        notes = notes == null ? "" : notes;
    }

    /** used when the sender is unknown */
    public static Contact unknown(String address, String displayName) {
        return new Contact(address, displayName, "unknown", Set.of(), "");
    }

    public boolean isKnown() {
        return !"unknown".equals(role);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(name.isBlank() ? address : name + " <" + address + ">");
        sb.append(" | role: ").append(role.isBlank() ? "unknown" : role);
        if (!tags.isEmpty()) {
            sb.append(" | tags: ").append(String.join(", ", tags));
        }
        if (!notes.isBlank()) {
            sb.append(" | notes: ").append(notes);
        }
        return sb.toString();
    }
}
