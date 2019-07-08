package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.Descriptor.PropertyType;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashBuildTriggerTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Test
  public void check_getters() throws Exception {
    StashBuildTrigger.DescriptorImpl descriptor = StashBuildTrigger.descriptor;

    // Field names from config.jelly
    Iterable<String> properties =
        Arrays.asList(
            "cron",
            "stashHost",
            "credentialsId",
            "projectCode",
            "repositoryName",
            "ignoreSsl",
            "targetBranchesToBuild",
            "checkDestinationCommit",
            "checkNotConflicted",
            "checkMergeable",
            "checkProbeMergeStatus",
            "mergeOnSuccess",
            "deletePreviousBuildFinishComments",
            "cancelOutdatedJobsEnabled",
            "ciSkipPhrases",
            "onlyBuildOnComment",
            "ciBuildPhrases");

    for (String property : properties) {
      System.out.println(property);
      PropertyType propertyType = descriptor.getPropertyType(property);
      assertThat(propertyType, is(notNullValue()));
    }
  }
}
