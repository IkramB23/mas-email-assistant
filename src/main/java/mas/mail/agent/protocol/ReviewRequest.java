package mas.mail.agent.protocol;

import mas.mail.model.Email;

import java.util.List;

/**
 * asks the reviewer to check a draft
 *
 * @param email            email being answered
 * @param draft            proposed reply
 * @param forbiddenTerms   information that must not be shared (agent rules and user rules)
 * @param maxWords         max length
 * @param expectedLanguage "fr", "en" or "auto"
 */
public record ReviewRequest(Email email, DraftProposal draft, List<String> forbiddenTerms, int maxWords,
                            String expectedLanguage) {
}
