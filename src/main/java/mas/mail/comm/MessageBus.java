package mas.mail.comm;

import mas.mail.agent.Agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * all agents talk through this bus (Mediator pattern)
 * agents only know the ids of the others, never their classes
 * this keeps the parts loosely linked
 * agents can be added or removed while the program runs
 * <p>
 * messages are sent one by one and the sender waits for the answer
 * every message is also sent to the {@link MessageListener}s (Observer pattern)
 * this gives a full trace of what happened, easy to explain
 */
public final class MessageBus {

    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    public synchronized void register(Agent agent) {
        agents.put(agent.id(), agent);
        agent.attach(this);
    }

    public synchronized void unregister(String agentId) {
        agents.remove(agentId);
    }

    public synchronized Optional<Agent> lookup(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public synchronized Collection<Agent> agents() {
        return List.copyOf(agents.values());
    }

    public void addListener(MessageListener l) {
        listeners.add(l);
    }

    public void removeListener(MessageListener l) {
        listeners.remove(l);
    }

    /**
     * sends a message and returns the answer
     * can return null for INFORM messages
     */
    public AclMessage send(AclMessage message) {
        publish(message);
        Agent receiver;
        synchronized (this) {
            receiver = agents.get(message.receiver());
        }
        AclMessage answer = receiver == null
                ? message.reply(Performative.FAILURE, "Unknown agent: " + message.receiver())
                : receiver.handle(message);
        if (answer != null) {
            publish(answer);
        }
        return answer;
    }

    /**
     * broadcast for the Contract Net: sends the CFP to every agent that passes the filter
     * returns all the answers (PROPOSE or REFUSE)
     */
    public List<AclMessage> broadcast(AclMessage cfp, Predicate<Agent> filter) {
        List<AclMessage> answers = new ArrayList<>();
        for (Agent a : agents()) {
            if (!a.id().equals(cfp.sender()) && filter.test(a)) {
                AclMessage answer = send(cfp.withReceiver(a.id()));
                if (answer != null) {
                    answers.add(answer);
                }
            }
        }
        return answers;
    }

    private void publish(AclMessage m) {
        for (MessageListener l : listeners) {
            try {
                l.onMessage(m);
            } catch (RuntimeException ignored) {
                // a broken observer must never break the organisation
            }
        }
    }
}
