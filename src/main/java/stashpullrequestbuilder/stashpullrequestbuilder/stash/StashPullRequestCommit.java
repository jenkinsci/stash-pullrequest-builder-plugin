package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestCommit {

	private String displayId;
	@JsonIgnore
	private List<StashPullRequestCommit> parentCommits;
	
    @JsonProperty("displayId")
    public String getDisplayId()
    {
        return displayId;
    }

    @JsonProperty("displayId")
    public void setDisplayId(String id)
    {
        this.displayId = id;
    }
    
    @JsonProperty("parents")
    public List<StashPullRequestCommit> getParentCommits()
    {
        return parentCommits;
    }
    
    @JsonProperty("parents")
    public void setParentCommits(List<StashPullRequestCommit> parentCommits)
    {
        this.parentCommits = parentCommits;
    }
}
