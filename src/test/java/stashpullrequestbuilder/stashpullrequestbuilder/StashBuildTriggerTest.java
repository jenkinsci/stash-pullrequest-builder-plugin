package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import hudson.model.Descriptor.PropertyType;
import hudson.model.FreeStyleProject;
import java.util.Arrays;
import java.util.Collections;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashBuildTriggerTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StaplerRequest makeStaplerRequest() {
    ServletContext servletContext = mock(ServletContext.class);
    WebApp webApp = new WebApp(servletContext);

    Stapler stapler = mock(Stapler.class);
    lenient().when(stapler.getWebApp()).thenReturn(webApp);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);

    return new RequestImpl(stapler, servletRequest, Collections.emptyList(), null);
  }

  @Test
  public void pipeline_jobs_not_supported_by_default() throws Exception {
    JSONObject json = new JSONObject();

    StaplerRequest staplerRequest = makeStaplerRequest();
    StashBuildTrigger.DescriptorImpl descriptor = new StashBuildTrigger.DescriptorImpl();
    descriptor.configure(staplerRequest, json);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    assertThat(descriptor.isApplicable(project), is(true));

    WorkflowJob workflowJob = jenkinsRule.createProject(WorkflowJob.class);
    assertThat(descriptor.isApplicable(workflowJob), is(false));
  }

  @Test
  public void pipeline_jobs_not_supported_when_pipeline_support_disabled() throws Exception {
    JSONObject json = new JSONObject();
    json.put("enablePipelineSupport", "false");

    StaplerRequest staplerRequest = makeStaplerRequest();
    StashBuildTrigger.DescriptorImpl descriptor = new StashBuildTrigger.DescriptorImpl();
    descriptor.configure(staplerRequest, json);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    assertThat(descriptor.isApplicable(project), is(true));

    WorkflowJob workflowJob = jenkinsRule.createProject(WorkflowJob.class);
    assertThat(descriptor.isApplicable(workflowJob), is(false));
  }

  @Test
  public void pipeline_jobs_supported_when_pipeline_support_enabled() throws Exception {
    JSONObject json = new JSONObject();
    json.put("enablePipelineSupport", "true");

    StaplerRequest staplerRequest = makeStaplerRequest();
    StashBuildTrigger.DescriptorImpl descriptor = new StashBuildTrigger.DescriptorImpl();
    descriptor.configure(staplerRequest, json);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    assertThat(descriptor.isApplicable(project), is(true));

    WorkflowJob workflowJob = jenkinsRule.createProject(WorkflowJob.class);
    assertThat(descriptor.isApplicable(workflowJob), is(true));
  }

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
