# Deployments
Deployment of this application uses [Github Deployment Environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment).

A successful deployment relies on the correct variables being defined in order to deploy to the correct AWS account etc.

### Managing Environments
You can view the current environment settings by visiting https://github.com/aodn/sample-django-app/settings/environments.

You can view the currently defined variables there or from the cli using:
```bash
gh variable list -R aodn/sample-django-app -e staging
```

### Updating Variables
Manually updating vars can be tedious and error-prone. Instead, you may define the variables you need as a .env file and push these values:
```bash
gh variable set -R aodn/sample-django-app -e staging -f staging.env
```

### Deploying From the CLI
The Terragrunt module for this application depends on variables being present and will fail if they are not.

To test locally, you may want to populate these into your local environment using the following command:
```bash
set -a; source ./github/staging.env; set +a
terragrunt plan
terragrunt apply
```

### Using Docker
A Dockerfile is provided to simplify local deployment, removing the need to install the required binaries on the local system.

N.B. The Dockerfile does assume a valid AWS CLI configuration.

### Example deployment
Modify the environment variables in `dev.env`:
```text
ALB_PARAMETER_NAME=shared-alb-dev-sydney
APP_NAME=sample-django-app-mybranch
AWS_ACCOUNT_ID=123456789012
AWS_REGION=ap-southeast-2
ECR_PARAMETER_NAME=api
ECR_REGISTRY=123456789012.dkr.ecr.ap-southeast-2.amazonaws.com
ECR_REPOSITORY=api
ENVIRONMENT=mydev-stack
RDS_PARAMETER_NAME=db01/primary/development
```

```bash
export AWS_PROFILE=myprofile
cd deploy
docker-compose -f docker-compose.yml run terragrunt
```

On the container run the following:
```bash
set -a; source ./github/dev.env; set +a
TF_VAR_image=latest terragrunt plan -out=tf.plan
TF_VAR_image=latest terragrunt apply -auto-approve tf.plan
```
