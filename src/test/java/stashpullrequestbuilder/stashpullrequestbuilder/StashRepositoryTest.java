package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.AbstractProject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepositoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class StashRepositoryTest {

  private StashRepository stashRepository;

  @Mock private StashBuildTrigger trigger;
  @Mock private AbstractProject<?, ?> project;
  @Mock private StashApiClient stashApiClient;
  @Mock private StashPullRequestResponseValue pullRequest;
  @Mock private StashPullRequestResponseValueRepository repository;
  @Mock private StashPullRequestResponseValueRepositoryRepository repoRepo;

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();

  @Before
  public void before() {
    stashRepository = new StashRepository(project, trigger, stashApiClient);
  }

  @Test
  public void getTargetPullRequestsReturnsEmptyListForNoPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.emptyList());

    assertThat(stashRepository.getTargetPullRequests(), empty());

    verify(stashApiClient, times(1)).getPullRequests();
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

    verify(stashApiClient, times(1)).getPullRequests();
    verify(pullRequest, times(1)).getTitle();
  }

  @Test
  public void getTargetPullRequestsSkipsClosedPullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn("CLOSED");

    assertThat(stashRepository.getTargetPullRequests(), empty());

    verify(stashApiClient, times(1)).getPullRequests();
    verify(pullRequest, times(0)).getTitle();
  }

  @Test
  public void getTargetPullRequestsSkipsNullStatePullRequests() {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.singletonList(pullRequest));
    when(pullRequest.getState()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), empty());

    verify(stashApiClient, times(1)).getPullRequests();
    verify(pullRequest, times(0)).getTitle();
  }
}
