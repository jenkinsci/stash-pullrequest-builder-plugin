package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.ArrayList;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * If pull request is mergeable
 * https://developer.atlassian.com/static/rest/stash/3.9.2/stash-rest.html#idp2785024
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestMergeableResponse {

  private Boolean canMerge;
  private Boolean conflicted;
  private ArrayList<StashPullRequestMergeableVetoMessage> vetoes;

  public Boolean getCanMerge() {
    return canMerge;
  }

  public void setCanMerge(Boolean canMerge) {
    this.canMerge = canMerge;
  }

  public Boolean getConflicted() {
    return conflicted;
  }

  public void setConflicted(Boolean conflicted) {
    this.conflicted = conflicted;
  }

  public ArrayList<StashPullRequestMergeableVetoMessage> getVetoes() {
    return vetoes;
  }

  public void setVetoes(ArrayList<StashPullRequestMergeableVetoMessage> vetoes) {
    this.vetoes = vetoes;
  }
}
