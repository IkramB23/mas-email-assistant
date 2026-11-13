package mas.mail.comm;

/** observer told about every message on the bus (GUI trace, decision log...) */
@FunctionalInterface
public interface MessageListener {
    void onMessage(AclMessage message);
}
