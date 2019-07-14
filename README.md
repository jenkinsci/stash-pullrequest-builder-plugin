Stash Pull Request Builder Plugin
================================

[![Join the chat at https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/stash-pullrequest-builder-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/stash-pullrequest-builder-plugin/master)](https://ci.jenkins.io/job/Plugins/job/stash-pullrequest-builder-plugin/job/master/)

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- Official [Jenkins Plugin Page](https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin)

## Prerequisites

- Jenkins 2.60.3 or higher.
- [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

## Environment variables

The plugin provides following environment variables to the build:

- `${pullRequestId}`
- `${pullRequestTitle}`
- `${sourceBranch}`
- `${targetBranch}`
- `${sourceRepositoryOwner}`
- `${sourceRepositoryName}`
- `${destinationRepositoryOwner}`
- `${destinationRepositoryName}`
- `${sourceCommitHash}`
- `${destinationCommitHash}`

If the project has a parameter with the name of one of those environment variables, the value of the parameter is replaced with the value of that environment variable.

Of those variables, `${destinationRepositoryOwner}` and `${destinationRepositoryName}` are available in all configuration fields. For instance, they can be used in the repository browser URL:
`http://stash.example.com/projects/${destinationRepositoryOwner}/repos/${destinationRepositoryName}/`

Other variables are only available in the fields evaluated in context of a specific build, e.g. in the git repository URL or in the shell commands to be run.

## Creating a Job

**Source Code Management**

Select *Git* then configure:

- **Repository URL**: `git@example.com:/${sourceRepositoryOwner}/${sourceRepositoryName}.git`
- **Advanced -> Refspec**: `+refs/pull-requests/${pullRequestId}/*:refs/remotes/origin/pr/${pullRequestId}/*`
- **Branch Specifier**: `${sourceCommitHash}`

**Build Triggers**

Select *Stash Pull Request Builder* then configure:

- **Cron schedule**: How often to poll, e.g. every 2 minute: `H/2 * * * *`
- **Stash URL**: The *http* or *https* URL of the Stash REST API (*NOT* ssh), e.g. *https://stash.example.com/*
- **Stash credentials**: Select or add username and password for the Stash REST API (*NOT* an ssh key).
- **Project**: Project key (i.e. an abbreviated project name), e.g. *PRJ* or *~user*
- **RepositoryName**: Name of the Stash repository to be polled, e.g. *Repo*

**Advanced options**
- **Ignore SSL certificates**: Allow invalid or unverifiable (e.g. self-signed) certificates to access Stash REST API over https.
- **Build PR targeting only these branches**: Comma separated list of branch names (or regexes), blank for all branches.
- **Rebuild if destination branch changes**: Start the build if the destination commit has changed, i.e. some changes have been made on the target branch.
- **Build only if Stash reports no conflicts**: Don't build PRs in the "conflict" state. This should be set if using the merge refspec, as Stash doesn't provide the merge refspec for conflicted PRs.
- **Build only if Stash reports PR is mergeable**: Build if the PR only if Stash allows merging it. *NOTE:* If the PR doesn't have the required number of approvals, the PR would not be tested when this option is enabled.
- **Probe Stash for merge status**: This just probes the Stash REST API endpoint that causes recalculation of Git refspecs (see [JENKINS-35219](https://issues.jenkins-ci.org/browse/JENKINS-35219) and [Atlassian KB 239988](https://answers.atlassian.com/questions/239988/change-pull-request-refs-after-commit-instead-of-after-approval-or-workaround) for details). Use this if you encounter problems with stale commits being built, but don't want to skip builds based on the PR status (as would be the case with the two options above). Also note that this option does not have any special effect if you have enabled one of the two options above.
- **Merge PR if build is successful**: Tell Stash to merge the PR automatically if the build job has been successful.
- **Keep PR comment only for most recent build**: Delete old comments about finished PR builds when starting a new build.
- **Cancel outdated jobs**: Cancel all jobs in the queue for the same PR.
- **Phrase to disable builds**: Don't build the PR if the specified phrase has been posted in a PR comment. Default: *NO TEST*
- **Only build if asked with the build phrase**: Only trigger the build when the build phrase has been posted.
- **Phrase to request a build**: Force (re-)building the PR if the specified phrase has been posted as a PR comment in Stash. This is useful when a build fails due to circumstances unrelated to the codebase. Starting a build in Jenkins GUI won't work, as the pull request data won't be available. Default: *test this please*

## Building the merge of Source Branch into Target Branch

You may want Jenkins to build the merged PR (that is the merge of `sourceBranch` into `targetBranch`) to catch any issues resulting from the merge.

### Letting Stash do the merge

To do this, change the Branch Specifier to `origin/pr/${pullRequestId}/merge`

**IMPORTANT: Enable "Build only if Stash reports no conflicts" in the advanced settings.** You can end up building a wrong commit if you don't enable that option. This option will enable a call to Stash REST API to make it update the "merge" refspec.

You probably also want to enable "Rebuild if destination branch changes" to make sure Jenkins rechecks the PR if other updates are made to the target branch.

This approach makes it possible to test pull requests when the source and the destination repositories are different.

The downside is less flexibility. You cannot specify how the merge is performed. Also, if a force push is made to the repository while this plugin is operating, the merge refspec can represent a newer PR revision than the one received by this plugin as `sourceCommitHash`.

### Merging locally

The other option is to do the merge in Jenkins as part of the build. This would only work if the source and the destination repositories are the same.

- To pull the target branch, change "Refspec" in "Source Code Management" > "Git" > "Repositories" -> "Advanced" to
  `+refs/pull-requests/${pullRequestId}/*:refs/remotes/origin/pr/${pullRequestId}/* +refs/heads/${targetBranch}:refs/remotes/origin/${targetBranch}`
- The "Branch Specifier" remains `${sourceCommitHash}`
- In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Build"
- In the "Name of repository" put "origin" or whatever in your remote repository's name. Note: unlike in the main part of the Git Repository config, you cannot leave this item blank for the default name.
- Set the "Branch to merge to" to `${targetBranch}`

An alternative to the "Merge Before Build" is running `git merge --no-edit $destinationCommitHash` in the beginning of the "Execute shell" section.

If you have downstream jobs that are not triggered by this plugin, you can simply add a condition on this command to check if the parameters are available:

```
if [ ! -z "$destinationCommitHash" ]; then
    git merge --no-edit $destinationCommitHash
fi
```

## Notify Stash of build result

If you are using the [StashNotifier plugin](https://wiki.jenkins-ci.org/display/JENKINS/StashNotifier+Plugin) and have enabled the 'Notify Stash Instance' Post-build Action while building the merged PR, you need to set `${sourceCommitHash}` as Commit SHA-1 to record the build result against the source commit.

## Rerun test builds

If you want to rerun pull request test, write *"test this please"* comment to your pull request.

## Adding additional parameters to a build

If you want to add additional parameters to the triggered build, add comments using the pattern `p:<parameter_name>=<value>`, one at each line, prefixed with `p:`. If the same parameter name appears multiple times, the latest comment with that parameter will set the value.

For security reasons, all the parameters should also be defined in the project. Select **This project is parameterized** in the configuration and add string parameters with the names you want to be read from the comments. Parameters with names not defined in the project will be ignored.

Parameters from the pull request comments are not allowed to override the environment variables provided by the plugin.

**Example:**

    test this please
    p:country=USA
    p:env=dev1


## Post Build Comment

It is possible to add a post build action that gives the option to post additional information to Stash when a build has been either successful or failed.
These comments can contain environment variables that will be translated when posted to Stash.

This feature can be used to post for instance a URL to the deployed application or code coverage at a successful build and why the build failed like what tests that did not pass.

## Pipeline support

Support for Jenkins pipelines is currently experimental. To enable it, go to *Manage Jenkins*, then *Configure System* and check for *Enable Pipeline Support*.

The configuration is the same as for other projects, but it is not currently possible to customize the comments posted to the Bitbucket Server after the job completion.

Stash Pull Request Builder Plugin could serve as a stepping stone to a more advanced setup using Bitbucket Branch Source Plugin. Unlike the later, this plugin supports inline Groovy scripts for pipeline configuration, which may be handy while figuring out the optimal configuration for the build. Once that configuration is established, it can be added to the sources as `Jenkinsfile`.

Stash Pull Request Builder Plugin makes conversion to pipelines easier the users, as it behaves the same way as before, it just triggers a pipeline project instead of a freestyle project. This plugin works with pull requests only and relies on comments for deciding when and what to build. It would not scan the git repository for all branches and build them all. It will never build a branch before a pull request is created from it.

To use Stash Pull Request Builder Plugin from Jenkinsfile, make sure to define it in properties. Otherwise, the trigger will be disabled after the the job runs. To find the exact syntax, open the Pipeline Syntax link and select "properties" as the Sample Step. Set up the trigger in the GUI and click on "Generate Pipeline Script".

## Copyright

Copyright © 2015 Nathan McCarthy.


## License

- BSD License
