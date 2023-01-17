**Download** 

Latest builds on the public TeamCity server compatible with:

- [TeamCity 2019.1.x](https://teamcity.jetbrains.com/buildConfiguration/TeamCityPluginsByJetBrains_AwsDeveloperToolsSupport_AwsCodePipelinePlugin_TeamCityKanpur20191x)
- [TeamCity > 2019.2](https://teamcity.jetbrains.com/buildConfiguration/TeamCityPluginsByJetBrains_AwsDeveloperToolsSupport_AwsCodePipelinePlugin_TeamCityTrunk)


**Plugin Description**
The plugin makes a TeamCity build a part of an [AWS CodePipeline](http://docs.aws.amazon.com/codepipeline/latest/userguide/welcome.html) stage by providing a custom job worker for the TeamCity Build and Test AWS CodePipeline actions.

It adds the AWS CodePipeline Action build trigger which polls the AWS CodePipeline for jobs. After the trigger detects a job, it adds a build to the queue. The build downloads input artifacts (depending on the AWS CodePipeline TeamCity action settings), runs the configured build steps and, in case of a successful build, publishes output artifacts to the AWS S3 for usage in the subsequent CodePipeline stages.

See [Building End-to-End Continuous Delivery and Deployment Pipelines in AWS and TeamCity](https://aws.amazon.com/blogs/devops/building-end-to-end-continuous-delivery-and-deployment-pipelines-in-aws-and-teamcity/) for step-by-step instructions.


**TeamCity Versions Compatibility**

The plugin is compatible with TeamCity 9.1 and newer.


**Installation instructions**
Download aws-pipeline-plugin.zip and [install the plugin](https://www.jetbrains.com/help/teamcity/installing-additional-plugins.html#InstallingAdditionalPlugins-InstallingTeamCityplugins) on the TeamCity server. 

**Prerequisites**

To use the plugin, you need to have correctly pre-configured AWS resources including:
- An IAM user or a role with sufficient permissions for TeamCity to access AWS services
- A CodePipeline pipeline.


For more information on configuring a CodePipeline pipeline and the required resources, see [CodePipeline documentation](http://docs.aws.amazon.com/codepipeline/latest/userguide/getting-started.html).

You may be charged money for using the above-mentioned resources.


**Security settings**


The currently supported [credentials types](http://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html) are AWS account access keys (access key ID and secret access key) or temporary access keys received from the AWS security token service by assuming a role.

Both types are supported by the AWS CodePipeline Action build trigger via [the default credential provider chain](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html#id1).

**ActionID property**

To identify an action when making requests to the CodePipeline, the plugin needs the ActionID property. The value must be unique and match the corresponding field in the TeamCity Action settings in the CodePipeline,  and satisfy the regular expression pattern: `[a-zA-Z0-9_-]+]` and have length <= 20.
 
**Action input and output artifacts**

CodePipeline TeamCity Build and Test actions can have from 0 to 5 input and/or output artifacts.
If any input artifacts are configured for the corresponding CodePipeline TeamCity action, they are downloaded from the S3 to the temporary directory before the build starts. The folder is specified by the codepipeline.artifact.input.folder [configuration parameter](https://www.jetbrains.com/help/teamcity/configuring-build-parameters.html) which is by default `%system.teamcity.build.tempDir%/CodePipeline/input`.

In the directory each input artifact can be found by artifact name, e.g. if TeamCity CodePipeline action is a part of a pipeline, has an input artifact named MyApp and the previous action has uploaded some zip file for this artifact name - then during the corresponding TeamCity build, the artifact will be available as `%codepipeline.artifact.input.folder%/MyApp.zip`.

Similarly, after the build finishes, the files found under the artifact output folder specified by the codepipeline.artifact.output.folder [configuration parameter](https://www.jetbrains.com/help/teamcity/configuring-build-parameters.html) (which is `%system.teamcity.build.tempDir%/CodePipeline/output` by default) are uploaded to the S3. Each artifact must be represented by an <artifact_name>.zip archive, e.g. to publish some zip file as an artifact named MyAppBuild, place it to `%codepipeline.artifact.output.folder%/MyAppBuild.zip`. You can achieve this, for example, by adding a Command line build step to your build which runs 

`cp MyAppBuild.zip %codepipeline.artifact.output.folder%/`

It's recommended by the AWS to use one of zip, tar, tar.gz (tgz) archive types to package artifacts for the AWS CodePipeline.


**Trigger poll interval**
By default TeamCity build triggers are polled every 20 seconds. To change this period for the AWS CodePipeline Action build trigger, specify `codepipeline.poll.interval` [configuration parameter](https://www.jetbrains.com/help/teamcity/configuring-build-parameters.html).


**Development links**

Public repository: https://github.com/JetBrains/teamcity-aws-codepipeline-plugin.
