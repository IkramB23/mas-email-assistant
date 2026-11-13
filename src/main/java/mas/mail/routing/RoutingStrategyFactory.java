package mas.mail.routing;

import mas.mail.llm.LlmClient;
import mas.mail.llm.LlmOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * builds the routing strategy from a text like {@code "rules:1.0,llm:1.0,thread:0.5"}
 * new strategies are added with {@link #register(String, Supplier)}
 */
public final class RoutingStrategyFactory {

    private final Map<String, Supplier<RoutingStrategy>> registry = new LinkedHashMap<>();

    public RoutingStrategyFactory(LlmClient llm, LlmOptions options) {
        register("rules", ContractNetStrategy::new);
        register("contract-net", ContractNetStrategy::new);
        register("llm", () -> new LlmClassifierStrategy(llm, options));
        register("thread", ThreadContinuityStrategy::new);
    }

    public void register(String name, Supplier<RoutingStrategy> supplier) {
        registry.put(name, supplier);
    }

    public RoutingStrategy create(String spec) {
        List<WeightedVotingStrategy.Voter> voters = new ArrayList<>();
        for (String part : spec.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] kv = item.split(":");
            Supplier<RoutingStrategy> s = registry.get(kv[0].trim());
            if (s == null) {
                throw new IllegalArgumentException("Unknown routing strategy '" + kv[0] + "', known: "
                        + registry.keySet());
            }
            double w = kv.length > 1 ? Double.parseDouble(kv[1].trim()) : 1.0;
            voters.add(new WeightedVotingStrategy.Voter(s.get(), w));
        }
        if (voters.isEmpty()) {
            throw new IllegalArgumentException("Empty routing strategy specification");
        }
        return new WeightedVotingStrategy(voters);
    }
}
