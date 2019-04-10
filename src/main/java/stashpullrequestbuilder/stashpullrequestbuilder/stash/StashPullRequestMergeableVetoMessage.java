package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * If pull request is mergeable
 * https://developer.atlassian.com/static/rest/stash/3.9.2/stash-rest.html#idp2785024
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestMergeableVetoMessage {

  private String summaryMessage;
  private String detailedMessage;

  public String getSummaryMessage() {
    return summaryMessage;
  }

  public void setSummaryMessage(String summaryMessage) {
    this.summaryMessage = summaryMessage;
  }

  public String getDetailedMessage() {
    return detailedMessage;
  }

  public void setDetailedMessage(String detailedMessage) {
    this.detailedMessage = detailedMessage;
  }
}
