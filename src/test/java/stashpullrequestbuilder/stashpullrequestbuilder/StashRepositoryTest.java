package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import hudson.model.AbstractProject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepositoryBranch;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepositoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class StashRepositoryTest {

  private StashRepository stashRepository;
  private StashPullRequestResponseValue pullRequest;
  private StashPullRequestResponseValueRepository repository;
  private StashPullRequestResponseValueRepositoryBranch branch;
  private StashPullRequestResponseValueRepositoryRepository repoRepo;
  private List<StashPullRequestResponseValue> pullRequestList;

  @Mock private StashBuildTrigger trigger;
  @Mock private AbstractProject<?, ?> project;
  @Mock private StashApiClient stashApiClient;

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Before
  public void before() {
    stashRepository = new StashRepository(project, trigger, stashApiClient);

    branch = new StashPullRequestResponseValueRepositoryBranch();
    branch.setName("feature/add-bloat");

    repoRepo = new StashPullRequestResponseValueRepositoryRepository();

    repository = new StashPullRequestResponseValueRepository();
    repository.setBranch(branch);
    repository.setRepository(repoRepo);

    pullRequest = new StashPullRequestResponseValue();
    pullRequest.setFromRef(repository);
    pullRequest.setToRef(repository);
    pullRequest.setState("OPEN");
    pullRequest.setTitle("Add some bloat");

    pullRequestList = Collections.singletonList(pullRequest);
  }

  @Test
  public void getTargetPullRequestsReturnsEmptyListForNoPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.emptyList());

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsOpenPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMergedPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    pullRequest.setState("MERGED");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsNullStatePullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    pullRequest.setState(null);

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranches() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,feature/.*,testing/.*");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranchesWithPadding() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild())
        .thenReturn("\trelease/.*, \n\tfeature/.* \r\n, testing/.*\r");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMismatchingBranches() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,testing/.*");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsEmpty() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsNull() {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInTitle() {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInComments() {
    StashPullRequestComment comment = new StashPullRequestComment();
    comment.setText("NO TEST");
    List<StashPullRequestComment> comments = Collections.singletonList(comment);

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);
    when(project.getDisplayName()).thenReturn("Pull Request Builder Project");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipPhraseIsCaseInsensitive() {
    pullRequest.setTitle("Disable any testing");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("disable ANY Testing");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipPhraseMatchedAsSubstring() {
    pullRequest.setTitle("This will get no testing whatsoever");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSupportsMultipleSkipPhrasesAndPadding() {
    pullRequest.setTitle("This will get no testing whatsoever");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("\tuntestable , \n NO TEST\t, \r\ndon't worry!");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsEmpty() {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsNull() {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }
}
