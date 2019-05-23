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
  private StashPullRequestResponseValueRepositoryBranch branch;

  @Mock private StashBuildTrigger trigger;
  @Mock private AbstractProject<?, ?> project;
  @Mock private StashApiClient stashApiClient;
  @Mock private StashPullRequestResponseValue pullRequest;
  @Mock private StashPullRequestResponseValueRepository repository;
  @Mock private StashPullRequestResponseValueRepositoryRepository repoRepo;

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Before
  public void before() {
    stashRepository = new StashRepository(project, trigger, stashApiClient);
    branch = new StashPullRequestResponseValueRepositoryBranch();
    branch.setName("feature/add-bloat");
  }

  @Test
  public void getTargetPullRequestsReturnsEmptyListForNoPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.emptyList());

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsOpenPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMergedPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("MERGED");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsNullStatePullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranches() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,feature/.*,testing/.*");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getBranch()).thenReturn(branch);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranchesWithPadding() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild())
        .thenReturn("\trelease/.*, \n\tfeature/.* \r\n, testing/.*\r");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getBranch()).thenReturn(branch);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMismatchingBranches() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,testing/.*");
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getBranch()).thenReturn(branch);

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsEmpty() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsNull() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn(null);
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInTitle() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("NO TEST");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInComments() {
    StashPullRequestComment comment = new StashPullRequestComment();
    comment.setText("NO TEST");
    List<StashPullRequestComment> comments = Collections.singletonList(comment);

    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(pullRequest.getTitle()).thenReturn("Add some bloat");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);
    when(project.getDisplayName()).thenReturn("Pull Request Builder Project");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipPhraseIsCaseInsensitive() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("Disable any testing");
    when(trigger.getCiSkipPhrases()).thenReturn("disable ANY Testing");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipPhraseMatchedAsSubstring() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("This will get no testing whatsoever");
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSupportsMultipleSkipPhrasesAndPadding() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("This will get no testing whatsoever");
    when(trigger.getCiSkipPhrases()).thenReturn("\tuntestable , \n NO TEST\t, \r\ndon't worry!");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsEmpty() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("NO TEST");
    when(trigger.getCiSkipPhrases()).thenReturn("");
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsNull() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("OPEN");
    when(pullRequest.getTitle()).thenReturn("NO TEST");
    when(trigger.getCiSkipPhrases()).thenReturn(null);
    when(pullRequest.getFromRef()).thenReturn(repository);
    when(pullRequest.getToRef()).thenReturn(repository);
    when(repository.getRepository()).thenReturn(repoRepo);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }
}
