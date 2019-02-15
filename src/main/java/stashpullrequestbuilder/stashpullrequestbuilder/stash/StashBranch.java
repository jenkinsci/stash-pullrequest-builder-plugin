package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashBranch {

	private String displayId;
	
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
    
}
