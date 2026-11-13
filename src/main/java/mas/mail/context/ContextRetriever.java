package mas.mail.context;

import mas.mail.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * picks which chunks of an agent's sources to send to the LLM
 * pinned chunks go first, then the most useful ones, until the token budget is full
 * this is the main way to avoid sending too much context to a small local model
 */
public final class ContextRetriever {

    private final List<ContextSource> sources;
    private final RelevanceScorer scorer;
    private final double minScore;

    public ContextRetriever(List<ContextSource> sources, RelevanceScorer scorer, double minScore) {
        this.sources = List.copyOf(sources);
        this.scorer = scorer;
        this.minScore = minScore;
    }

    public List<ContextChunk> retrieve(String query, int tokenBudget) {
        List<ContextChunk> all = new ArrayList<>();
        for (ContextSource s : sources) {
            all.addAll(s.chunks());
        }
        List<ContextChunk> selected = new ArrayList<>();
        int used = 0;
        for (ContextChunk c : all) {
            if (c.pinned()) {
                int cost = Text.estimateTokens(c.text());
                if (used + cost <= tokenBudget) {
                    selected.add(c);
                    used += cost;
                }
            }
        }
        List<ContextChunk> candidates = all.stream().filter(c -> !c.pinned()).toList();
        double[] scores = scorer.score(query, candidates);
        List<Integer> order = IntStream.range(0, candidates.size()).boxed()
                .filter(i -> scores[i] > minScore)
                .sorted(Comparator.comparingDouble((Integer i) -> scores[i]).reversed())
                .toList();
        for (int i : order) {
            ContextChunk c = candidates.get(i);
            int cost = Text.estimateTokens(c.text());
            if (used + cost <= tokenBudget) {
                selected.add(c);
                used += cost;
            }
        }
        return selected;
    }

    public List<ContextSource> sources() {
        return sources;
    }
}
