# Contributing to Modern Agent Viewer Plugin

Thank you for your interest in contributing to the Modern Agent Viewer Jenkins Plugin!

## Reporting Issues
Please report bugs and feature requests via [GitHub Issues](https://github.com/reder9/modern-agent-viewer-jenkins-plugin/issues).

## Development Setup
This plugin is built with Maven and Java 21+ using the modern Jenkins Plugin Parent POM.

### Building Locally
```bash
mvn clean verify
```

### Running Tests
```bash
mvn test
```

### Checking Code Quality & Security (SpotBugs)
```bash
mvn spotbugs:check
```

### Interactive Development Instance
To launch a live interactive Jenkins development instance with the plugin pre-installed:
```bash
mvn hpi:run
```
Then navigate to `http://localhost:8080/jenkins/`.

## Code & Design Guidelines
- **Zero Emojis**: The plugin strictly adheres to professional Jenkins UI design standards. Always use crisp vector SVG icons and semantic badge tokens.
- **Stylized Dialogs**: Never use native browser dialogs (`alert()`, `confirm()`, or `prompt()`). Use stylized modals or theme-matched toasts.
- **Security**: All state-modifying endpoints enforce `@RequirePOST` and explicit Jenkins permission checks (`Computer.CONFIGURE`, `Computer.DISCONNECT`, or `Item.CANCEL`). All dynamic data is escaped via `escapeHtml()` and Jelly `escape-by-default='true'`.
- **Zero Build Step**: Front-end JavaScript and CSS are standard vanilla ES2022 and native CSS variables without Node/Webpack build steps.

## Submitting Pull Requests
1. Fork the repository and create a descriptive feature branch.
2. Verify all JUnit 5 tests pass and SpotBugs reports 0 bugs.
3. Open a pull request against the `main` branch.
4. Continuous Delivery will automatically validate and release merged pull requests using JEP-229.
