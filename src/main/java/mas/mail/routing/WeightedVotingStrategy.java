package mas.mail.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * strategy made of other strategies (Composite pattern)
 * each one is a voter that gives a score to every agent (range voting)
 * the final score is the weighted mean of the voters that voted
 * this mixes different clues (agents' self check, LLM understanding, thread continuity)
 * and works better than any one of them alone
 */
public final class WeightedVotingStrategy implements RoutingStrategy {

    /** a voter and its weight */
    public record Voter(RoutingStrategy strategy, double weight) {
    }

    private final List<Voter> voters;

    public WeightedVotingStrategy(List<Voter> voters) {
        this.voters = List.copyOf(voters);
    }

    @Override
    public String name() {
        StringBuilder sb = new StringBuilder();
        for (Voter v : voters) {
            if (!sb.isEmpty()) {
                sb.append('+');
            }
            sb.append(v.strategy().name());
            if (v.weight() != 1.0) {
                sb.append(String.format(Locale.ROOT, "*%.1f", v.weight()));
            }
        }
        return sb.toString();
    }

    @Override
    public List<AgentScore> score(RoutingContext ctx) {
        Map<String, Double> sum = new LinkedHashMap<>();
        Map<String, List<String>> reasons = new LinkedHashMap<>();
        double totalWeight = 0;
        for (Voter v : voters) {
            List<AgentScore> ballot = v.strategy().score(ctx);
            if (ballot.isEmpty()) {
                continue; // no vote
            }
            totalWeight += v.weight();
            for (AgentScore s : ballot) {
                sum.merge(s.agentId(), v.weight() * s.score(), Double::sum);
                List<String> r = reasons.computeIfAbsent(s.agentId(), k -> new ArrayList<>());
                if (s.score() > 0) {
                    r.add(String.format(Locale.ROOT, "[%s %.2f] %s", v.strategy().name(), s.score(),
                            String.join("; ", s.reasons())));
                }
            }
        }
        if (totalWeight == 0) {
            return List.of();
        }
        List<AgentScore> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : sum.entrySet()) {
            out.add(new AgentScore(e.getKey(), e.getValue() / totalWeight, reasons.get(e.getKey())));
        }
        out.sort(Comparator.comparingDouble(AgentScore::score).reversed());
        return out;
    }
}
