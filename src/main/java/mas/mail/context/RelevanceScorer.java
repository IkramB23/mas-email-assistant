package mas.mail.context;

import java.util.List;

/**
 * strategy that ranks chunks for a query
 * the default one compares words (BM25)
 * a scorer based on embeddings (local embedding model) can be added without changing the agents
 */
public interface RelevanceScorer {

    /**
     * returns one score per chunk, in the same order
     * a higher score means more useful
     */
    double[] score(String query, List<ContextChunk> chunks);
}
