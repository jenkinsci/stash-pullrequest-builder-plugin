package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

@Extension
public class StashAditionalParameterEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        StashCause cause = (StashCause) r.getCause(StashCause.class);
        if (cause == null) {
            return;
        }

        putEnvVar(envs, "sourceBranch", cause.getSourceBranch());
        putEnvVar(envs, "targetBranch", cause.getTargetBranch());
        putEnvVar(envs, "sourceRepositoryOwner", cause.getSourceRepositoryOwner());
        putEnvVar(envs, "sourceRepositoryName", cause.getSourceRepositoryName());
        putEnvVar(envs, "pullRequestId", cause.getPullRequestId());
        putEnvVar(envs, "destinationRepositoryOwner", cause.getDestinationRepositoryOwner());
        putEnvVar(envs, "destinationRepositoryName", cause.getDestinationRepositoryName());
        putEnvVar(envs, "pullRequestTitle", cause.getPullRequestTitle());
        putEnvVar(envs, "sourceCommitHash", cause.getSourceCommitHash());
        putEnvVar(envs, "destinationCommitHash", cause.getDestinationCommitHash());

        super.buildEnvironmentFor(r, envs, listener);
    }

    private static void putEnvVar(EnvVars envs, String key, String value) {
        envs.put(key, Objects.toString(value, ""));
    }
}
