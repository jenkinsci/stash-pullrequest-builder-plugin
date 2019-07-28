package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValueRepositoryRepository {
  private String slug;
  private StashPullRequestResponseValueRepositoryProject project;

  @JsonProperty("project")
  public StashPullRequestResponseValueRepositoryProject getRepository() {
    return project;
  }

  @JsonProperty("project")
  public void setRepository(StashPullRequestResponseValueRepositoryProject project) {
    this.project = project;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  @Nullable
  public String getProjectName() {
    if (this.project != null) {
      return project.getKey();
    }
    return null;
  }

  public String getRepositoryName() {
    return this.slug;
  }
}
