package mas.mail.agent.protocol;

/**
 * which knowledge a specialist can use for a draft
 * everything is on in normal use
 * the experiments turn some parts off to see how much they help (ablation study)
 */
public record DraftOptions(boolean useContext, boolean useSystemPrompt, boolean useHistory) {

    public static final DraftOptions FULL = new DraftOptions(true, true, true);
}
