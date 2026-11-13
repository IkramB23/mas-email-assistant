package mas.mail.context;

import mas.mail.util.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** text score with the Okapi BM25 formula, computed on the chunks of one agent */
public final class Bm25Scorer implements RelevanceScorer {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    @Override
    public double[] score(String query, List<ContextChunk> chunks) {
        double[] scores = new double[chunks.size()];
        if (chunks.isEmpty()) {
            return scores;
        }
        Set<String> queryTerms = new HashSet<>(Text.tokenize(query));
        Map<String, Integer> docFreq = new HashMap<>();
        Map<Integer, Map<String, Integer>> termFreqs = new HashMap<>();
        int[] lengths = new int[chunks.size()];
        double avgLen = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> tokens = Text.tokenize(chunks.get(i).text() + " " + chunks.get(i).source());
            lengths[i] = tokens.size();
            avgLen += tokens.size();
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }
            termFreqs.put(i, tf);
            for (String t : tf.keySet()) {
                docFreq.merge(t, 1, Integer::sum);
            }
        }
        avgLen = Math.max(1, avgLen / chunks.size());
        int n = chunks.size();
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            double s = 0;
            for (String q : queryTerms) {
                int f = tf.getOrDefault(q, 0);
                if (f == 0) {
                    continue;
                }
                int df = docFreq.getOrDefault(q, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                s += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * lengths[i] / avgLen));
            }
            scores[i] = s;
        }
        return scores;
    }
}
