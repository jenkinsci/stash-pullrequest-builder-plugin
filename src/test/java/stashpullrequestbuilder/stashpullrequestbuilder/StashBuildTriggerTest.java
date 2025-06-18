package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.model.Descriptor.PropertyType;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.Secret;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebApp;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashBuildTriggerTest {

  static final String cron = "H/5 * * * *";
  static final String stashHost = "https://localhost/";
  static final String credentialsId = "credential-id";
  static final String projectCode = "PROJ";
  static final String repositoryName = "MyRepo";

  static final StashBuildTrigger.DescriptorImpl descriptor = StashBuildTrigger.descriptor;

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StaplerRequest2 makeStaplerRequest() {
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

    StaplerRequest2 staplerRequest = makeStaplerRequest();
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

    StaplerRequest2 staplerRequest = makeStaplerRequest();
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

    StaplerRequest2 staplerRequest = makeStaplerRequest();
    descriptor.configure(staplerRequest, json);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    assertThat(descriptor.isApplicable(project), is(true));

    WorkflowJob workflowJob = jenkinsRule.createProject(WorkflowJob.class);
    assertThat(descriptor.isApplicable(workflowJob), is(true));
  }

  @Test
  public void check_getters() throws Exception {
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
      PropertyType propertyType = descriptor.getPropertyType(property);
      assertThat(propertyType, is(notNullValue()));
    }
  }

  @Test
  public void constructor_sets_required_properties() throws Exception {
    StashBuildTrigger stashBuildTrigger =
        new StashBuildTrigger(cron, stashHost, credentialsId, projectCode, repositoryName);

    assertThat(stashBuildTrigger.getCron(), is(cron));
    assertThat(stashBuildTrigger.getStashHost(), is(stashHost));
    assertThat(stashBuildTrigger.getCredentialsId(), is(credentialsId));
    assertThat(stashBuildTrigger.getProjectCode(), is(projectCode));
    assertThat(stashBuildTrigger.getRepositoryName(), is(repositoryName));
  }

  @Test
  public void start_works_with_valid_credentials() throws Exception {
    StandardUsernamePasswordCredentials credentials =
        mock(StandardUsernamePasswordCredentials.class);

    when(credentials.getId()).thenReturn(credentialsId);
    when(credentials.getUsername()).thenReturn("Username");
    when(credentials.getPassword()).thenReturn(Secret.fromString("Password"));

    SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

    StashBuildTrigger stashBuildTrigger =
        new StashBuildTrigger(cron, stashHost, credentialsId, projectCode, repositoryName);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    stashBuildTrigger.start(project, true);

    assertThat(stashBuildTrigger.getRepository(), is(notNullValue()));
  }

  @Test
  public void start_fails_with_empty_credentialId() throws Exception {
    StashBuildTrigger stashBuildTrigger =
        new StashBuildTrigger(cron, stashHost, "", projectCode, repositoryName);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    stashBuildTrigger.start(project, true);

    assertThat(stashBuildTrigger.getRepository(), is(nullValue()));
  }

  @Test
  public void start_fails_with_unknown_credentialId() throws Exception {
    StashBuildTrigger stashBuildTrigger =
        new StashBuildTrigger(cron, stashHost, credentialsId, projectCode, repositoryName);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    stashBuildTrigger.start(project, true);

    assertThat(stashBuildTrigger.getRepository(), is(nullValue()));
  }

  @Test
  public void nonempty_credential_accepted() throws Exception {
    FormValidation formValidation = descriptor.doCheckCredentialsId("credential-id");
    assertThat(formValidation.kind, is(Kind.OK));
    assertThat(formValidation.getMessage(), is(nullValue()));
  }

  @Test
  public void empty_credential_rejected() throws Exception {
    FormValidation formValidation = descriptor.doCheckCredentialsId("");
    assertThat(formValidation.kind, is(Kind.ERROR));
    assertThat(formValidation.getMessage(), is("Credentials cannot be empty"));
  }

  @Test
  public void null_credential_rejected() throws Exception {
    FormValidation formValidation = descriptor.doCheckCredentialsId(null);
    assertThat(formValidation.getMessage(), is("Credentials cannot be empty"));
  }
}
