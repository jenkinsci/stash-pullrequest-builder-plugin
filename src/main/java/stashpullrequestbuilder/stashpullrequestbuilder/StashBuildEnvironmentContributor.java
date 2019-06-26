package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import jenkins.model.ParameterizedJobMixIn;

@Extension
public class StashBuildEnvironmentContributor extends EnvironmentContributor {
  @Override
  public void buildEnvironmentFor(
      @Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    Run<?, ?> run = r;
    if (r instanceof AbstractBuild) {
      AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) r;
      run = build.getRootBuild();
    }

    StashCause cause = run.getCause(StashCause.class);
    if (cause != null) {
      for (Map.Entry<String, String> variable : cause.getEnvironmentVariables().entrySet()) {
        putEnvVar(envs, variable.getKey(), variable.getValue());
      }
    }

    super.buildEnvironmentFor(r, envs, listener);
  }

  @Override
  public void buildEnvironmentFor(
      @Nonnull Job job, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {

    StashBuildTrigger trigger = ParameterizedJobMixIn.getTrigger(job, StashBuildTrigger.class);
    if (trigger != null) {
      putEnvVar(envs, "destinationRepositoryOwner", trigger.getProjectCode());
      putEnvVar(envs, "destinationRepositoryName", trigger.getRepositoryName());
    }

    super.buildEnvironmentFor(job, envs, listener);
  }

  private static void putEnvVar(EnvVars envs, String key, String value) {
    envs.put(key, Objects.toString(value, ""));
  }
}
