package mas.mail.memory;

import mas.mail.model.ThreadEntry;

import java.util.List;

/** saves the conversation threads on disk */
public interface ConversationRepository {

    /**
     * adds an entry
     * an entry with the same kind and message id is not added twice
     */
    void append(String threadId, ThreadEntry entry);

    /** entries of a thread, oldest first (empty list if the thread is unknown) */
    List<ThreadEntry> thread(String threadId);

    /** latest RECEIVED or SENT entries with one person, in other threads */
    List<ThreadEntry> recentWith(String address, String excludedThreadId, int limit);
}
