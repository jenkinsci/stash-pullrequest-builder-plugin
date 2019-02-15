package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashBranchCommitResponse {
	
	private List<StashBranch> branches;

    @JsonProperty("values")
    public List<StashBranch> getBranches() {
        return branches;
    }

    @JsonProperty("values")
    public void setBranches(List<StashBranch> branches) {
        this.branches = branches;
    }
}
