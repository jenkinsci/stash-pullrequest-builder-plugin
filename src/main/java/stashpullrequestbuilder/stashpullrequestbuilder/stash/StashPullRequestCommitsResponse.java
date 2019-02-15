package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestCommitsResponse {
	
	@JsonIgnore
    private List<StashPullRequestCommit> commitValues;
	
    @JsonProperty("values")
    public List<StashPullRequestCommit> getCommitValues() {
        return commitValues;
    }

    @JsonProperty("values")
    public void setCommitValues(List<StashPullRequestCommit> commitValues) {
        this.commitValues = commitValues;
    }
}
