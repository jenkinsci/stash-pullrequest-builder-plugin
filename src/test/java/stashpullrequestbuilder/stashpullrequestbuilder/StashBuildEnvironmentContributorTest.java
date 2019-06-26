package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Build;
import hudson.model.Job;
import hudson.model.Run;
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
public class StashBuildEnvironmentContributorTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StashBuildEnvironmentContributor contributor;

  private EnvVars envVars;
  private StashCause cause;

  @Mock private TaskListener listener;

  @Before
  public void before() {
    contributor = new StashBuildEnvironmentContributor();
    envVars = new EnvVars();
    cause =
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

  private void checkEnvVars() {
    assertThat(envVars.size(), is(10));

    assertThat(envVars, hasEntry("destinationCommitHash", "DestinationCommitHash"));
    assertThat(envVars, hasEntry("destinationRepositoryName", "DestinationRepositoryName"));
    assertThat(envVars, hasEntry("destinationRepositoryOwner", "DestinationRepositoryOwner"));
    assertThat(envVars, hasEntry("pullRequestId", "PullRequestId"));
    assertThat(envVars, hasEntry("pullRequestTitle", "PullRequestTitle"));
    assertThat(envVars, hasEntry("sourceBranch", "SourceBranch"));
    assertThat(envVars, hasEntry("sourceCommitHash", "SourceCommitHash"));
    assertThat(envVars, hasEntry("sourceRepositoryName", "SourceRepositoryName"));
    assertThat(envVars, hasEntry("sourceRepositoryOwner", "SourceRepositoryOwner"));
    assertThat(envVars, hasEntry("targetBranch", "TargetBranch"));
  }

  @Test
  public void populatesVariablesForRun() throws Exception {
    Run<?, ?> run = mock(Run.class);
    when(run.getCause(StashCause.class)).thenReturn(cause);

    contributor.buildEnvironmentFor(run, envVars, listener);
    checkEnvVars();
  }

  @Test
  public void noVariablesForRunWithoutCause() throws Exception {
    Run<?, ?> run = mock(Run.class);
    when(run.getCause(StashCause.class)).thenReturn(null);

    contributor.buildEnvironmentFor(run, envVars, listener);
    assertThat(envVars, is(anEmptyMap()));
  }

  @Test
  public void populatesVariablesForRootBuild() throws Exception {
    Build<?, ?> rootBuild = mock(Build.class);

    doReturn(rootBuild).when(rootBuild).getRootBuild();
    when(rootBuild.getCause(StashCause.class)).thenReturn(cause);

    contributor.buildEnvironmentFor(rootBuild, envVars, listener);
    checkEnvVars();
  }

  @Test
  public void populatesVariablesForChildBuild() throws Exception {
    Build<?, ?> childBuild = mock(Build.class);
    Build<?, ?> rootBuild = mock(Build.class);

    doReturn(rootBuild).when(childBuild).getRootBuild();
    when(rootBuild.getCause(StashCause.class)).thenReturn(cause);

    contributor.buildEnvironmentFor(childBuild, envVars, listener);
    checkEnvVars();
  }

  @Test
  public void noVariablesForJob() throws Exception {
    Job<?, ?> job = mock(Job.class);

    contributor.buildEnvironmentFor(job, envVars, listener);
    assertThat(envVars, is(anEmptyMap()));
  }
}
