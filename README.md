Stash Pull Request Builder Plugin
================================

[![Join the chat at https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/stash-pullrequest-builder-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/stash-pullrequest-builder-plugin/master)](https://ci.jenkins.io/job/Plugins/job/stash-pullrequest-builder-plugin/job/master/)

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- Official [Jenkins Plugin Page](https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin)

## Prerequisites

- Jenkins 1.532 or higher.
- [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

## Parameter variables

The plugin makes available to the job the following parameter variables:
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

## Creating a Job

**Source Code Management**

Select *Git* then configure:

- **Repository URL**: `git@example.com:/${sourceRepositoryOwner}/${sourceRepositoryName}.git`
- **Advanced -> Refspec**: `+refs/pull-requests/${pullRequestId}/*:refs/remotes/origin/pr/${pullRequestId}/*`
- **Branch Specifier**: `${sourceCommitHash}`

**Build Triggers**

Select *Stash Pull Request Builder* then configure:

- **Cron**: must be specified. eg: every 2 minute `H/2 * * * *`
- **Stash URL**: the *http* or *https* URL of the Stash REST API Host (NOT *ssh*). eg: *https://example.com*
- **Stash Credentials**: Select or Add the login username/password for the Stash REST API Host (NOT ssh key)
- **Project**: abbreviated project code. eg: *PRJ* or *~user*
- **RepositoryName**: eg: *Repo*

**Advanced options**
- Ignore ssl certificates:
- Build PR targeting only these branches: common separated list of branch names (or regexes). Blank for all.
- Rebuild if destination branch changes:
- Build only if Stash reports no conflicts: this should be set if using the merge branch to avoid issues with out-of-date merge branch in Stash
- Build only if PR is mergeable (note this will stop the PR being built if you have required approvers limit set >0 and the PR hasn't been approved)
- Cancel outdated jobs
- CI Skip Phrases: default: "NO TEST"
- Only build when asked (with test phrase):
- CI Build Phrases: default: "test this please"
- Target branches: a comma separated list of branches (e.g. brancha,branchb)

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

If you want to add additional parameters to the triggered build, add comments using the pattern `p:<parameter_name>=<value>`, one at each line, prefixed with `p:`. If the same parameter name appears multiple times the latest comment with that parameter will decide the value.

**Example:**

    test this please
    p:country=USA
    p:env=dev1


## Post Build Comment

It is possible to add a post build action that gives the option to post additional information to Stash when a build has been either successful or failed.
These comments can contain environment variables that will be translated when posted to Stash.

This feature can be used to post for instance a URL to the deployed application or code coverage at a successful build and why the build failed like what tests that did not pass.

## Copyright

Copyright © 2015 Nathan McCarthy.


## License

- BSD License
