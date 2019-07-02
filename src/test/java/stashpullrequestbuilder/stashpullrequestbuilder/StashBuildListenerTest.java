package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
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

@RunWith(MockitoJUnitRunner.class)
public class StashBuildListenerTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StashBuildListener stashBuildListener;

  private StashCause stashCause;
  private TaskListener taskListener;

  @Mock private FreeStyleBuild build;

  @Before
  public void before() throws Exception {
    stashBuildListener = new StashBuildListener();
    taskListener = jenkinsRule.createTaskListener();
    stashCause =
        new StashCause(
            "StashHost",
            "SourceBranch",
            "TargetBranch",
            "SourceRepositoryOwner",
            "SourceRepositoryName",
            "PullRequestId",
            "DestinationRepositoryOwner",
            "DestinationRepositoryName",
            "PullRequestTitle",
            "SourceCommitHash",
            "DestinationCommitHash",
            "BuildStartCommentId",
            "PullRequestVersion",
            null);
  }

  @Test
  public void onStarted_sets_description() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(stashCause);

    stashBuildListener.onStarted(build, taskListener);

    verify(build, times(1)).setDescription(anyString());
  }

  @Test
  public void onStarted_doesnt_set_if_there_is_no_StashCause() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(null);

    stashBuildListener.onStarted(build, taskListener);

    verify(build, never()).setDescription(anyString());
  }

  @Test
  public void onCompleted_doesnt_check_build_result_if_there_is_no_StashCause() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(null);

    stashBuildListener.onCompleted(build, taskListener);

    verify(build, never()).getResult();
  }
}
