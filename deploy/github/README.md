# Github Deployments
Deployment of this application uses [Github Deployment Environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment).

A successful deployment relies on the correct variables being defined in order to deploy to the correct AWS account etc.

### Managing Environments
You can view the current environment settings by visiting https://github.com/aodn/sample-django-app/settings/environments.

You can view the currently defined variables there or from the cli using:
```bash
gh variable list -R aodn/sample-django-app -e staging
```

#### Updating Variables
Manually updating vars can be tedious and error-prone. Instead, you may define the variables you need as a .env file and push these values:
```bash
gh variable set -R aodn/sample-django-app -e staging -f staging.env
```
