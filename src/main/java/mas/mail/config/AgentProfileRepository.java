package mas.mail.config;

import java.util.List;
import java.util.Optional;

/** stores the agent profiles (hides how they are saved) */
public interface AgentProfileRepository {

    List<AgentProfile> findAll();

    Optional<AgentProfile> findById(String id);

    /**
     * creates or updates a profile and its folder
     * returns the saved profile
     */
    AgentProfile save(AgentProfile profile);

    void delete(String id);
}
