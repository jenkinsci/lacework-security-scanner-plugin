# lacework-security-scanner

## Introduction

This Jenkins plugin enables easy integration with the Lacework security platform to perform container assurance assessments.

## Getting started

To configure this plugin, you will need to generate an Inline Scanner integration within the Lacework platform. To create the Inline Scanner integration, follow the instructions <a target="_blank" href="https://docs.lacework.com/integrate-inline-scanner">here</a>.

Once the Inline Scanner integration is created in the Lacework platform, you will need the Lacework account name and access token in order to configure the plugin. To configure, navigate to "Manage Jenkins" -> "Configure System", the scroll to the "Lacework Security" section. Input the Lacework account name and access token into the appropriate fields, then click Save.

Once the global configuration is complete, you can add the Lacework Security build step to your pipelines. This will allow container assurance and vulnerability assessments to take place during the build process, and fail builds (if desired) that do not abide by specified policies.

## Contributing

To contribute to this repository, please review the [CONTRIBUTING](CONTRIBUTING.md) file.

Also, please refer to the Jenkins [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
