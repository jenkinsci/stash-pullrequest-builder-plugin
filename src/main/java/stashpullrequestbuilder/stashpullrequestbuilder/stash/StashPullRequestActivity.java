package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Created by Nathan on 20/03/2015. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestActivity {
  private StashPullRequestComment comment;

  public StashPullRequestComment getComment() {
    return comment;
  }

  public void setComment(StashPullRequestComment comment) {
    this.comment = comment;
  }
}
