package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/** Created by Nathan McCarthy */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestActivityResponse {
  @JsonIgnore private List<StashPullRequestActivity> prValues;

  @JsonProperty("size")
  private Integer size;

  private Boolean isLastPage;

  private Integer nextPageStart;

  @JsonProperty("values")
  public List<StashPullRequestActivity> getPrValues() {
    return prValues;
  }

  @JsonProperty("values")
  public void setPrValues(List<StashPullRequestActivity> prValues) {
    this.prValues = prValues;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public Boolean getIsLastPage() {
    return isLastPage;
  }

  public void setIsLastPage(Boolean isLastPage) {
    this.isLastPage = isLastPage;
  }

  public Integer getNextPageStart() {
    return nextPageStart;
  }

  public void setNextPageStart(Integer nextPageStart) {
    this.nextPageStart = nextPageStart;
  }
}
