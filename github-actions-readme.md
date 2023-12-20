### AWS ECR-ECS Deploy 
Github actions workflow to build and deploy an application to AWS ECR-ECS and then publish artifact to AWS CodeArtifact.

#### Setup github branch
- Configure github branch for push or pull_request.

```yml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

#### Set up environment variables 
- Defining platform specific env vars, as the actual values for the below vars will be fetched from parameter store.

```yml
env:
  ECR_REPOSITORY: testrepo # set this to your Amazon ECR repository name
  ECS_SERVICE: ogcapi-java-service # set this to your Amazon ECS service name
  ECS_CLUSTER: aodn-imos-v2 # set this to your Amazon ECS cluster name
  ECS_TASK_DEFINITION: ./ogcapi-java-dev-td.json # set this to the path to your Amazon ECS task definition
  CONTAINER_NAME: aodn-dev-container # set this to the name of the container in the task definition
  CA_DOMAIN: testdomainname # set this with aws code artifact domain name
  CA_DOMAIN_OWNER: testdomainowner # set this with aws code artifact domain owner
  CA_REPO: testcarepo # set this with aws code artifact repo name
  CA_PACKAGE: testpackage # set this with aws code artifact package name
  CA_NAMESPACE: testnamespace # set this with aws code artifact namespace
```

#### AWS ssm parameter store 
- Fetch environment parameters(key & vaule pairs) from aws ssm parameter store 

```yml
- name: Retrieve Parameters - ssm parameter store
        id: getParameters
        run: |
          # Replace '<path to parameter store>' with your specific path from Parameter Store
          parameters=$(aws ssm get-parameters-by-path --path <path to parameter store> --recursive --query 'Parameters[*].[Name,Value]' --output json)
          echo "$parameters" > parameters.json
          echo "::set-output name=parameters_json::$parameters"
```
- Once all the parameters are pulled, perform and process through jquery as key/value pairs overriding env vars.
```yml
if [ "$name" = "/core/ogcapi/dev_ecr_ecs_config/ecr_repo" ]; then
              echo "ECR_REPOSITORY=$value" >> "$GITHUB_ENV"
fi 
```

#### Prepare Build Id
- This step in github pipeline will generate build id for any changes to the repo.
- Build Id format: `${BRANCH}-${REVISION}-${TS} - Branch-Revision-Timestamp`

```yml
- name: Prepare Build-ID  
        id: prep
        run: |
          BRANCH=${GITHUB_REF##*/}
          TS=$(date +%s)
          REVISION=${GITHUB_SHA::8}
          BUILD_ID="${BRANCH}-${REVISION}-${TS}"
          LATEST_ID=canary
          if [[ $GITHUB_REF == refs/tags/* ]]; then
            BUILD_ID=${GITHUB_REF/refs\/tags\//}
            LATEST_ID=latest
          fi
          echo "BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
          echo "LATEST_ID=$LATEST_ID"
          echo "BUILD_ID=$BUILD_ID" >> $GITHUB_OUTPUT
```

#### Set up JDK 17
```yml
- name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'
```

#### Build with maven
```yml
- name: Build with Maven
        run: |
          mvn -B package --file pom.xml
```

#### Build docker image 
```yml
- name: Build and tag image
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ steps.prep.outputs.BUILD_ID }}
        run: |
          # build a docker container to be deployed to ecr-ecs.
          docker build -t $ECR_REPOSITORY:$IMAGE_TAG .
          echo "image=$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
```

#### Perform security scans
- This step will run vulnerability scanning for docker image, `severity` can be configured to `LOW,HIGH,CRITICAL`
- `continue-on-error` set to `true` will continue the workflow if any erros are found in the scan results

```yml
- name: Run Trivy vulnerability scanner in docker mode
        uses: aquasecurity/trivy-action@master
        with:
            image-ref: ${{ steps.build-image.outputs.image }}
            format: 'table'
            severity: 'HIGH,CRITICAL'
            vuln-type: 'os,library'
            exit-code: 1
            ignore-unfixed: true
        continue-on-error: true
```

#### Push image to ECR 
```yml
- name: Push image to Amazon ECR
        id: push-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ steps.prep.outputs.BUILD_ID }}
        run: |
          # push image to aws ecr
          docker push $ECR_REPOSITORY:$IMAGE_TAG
          echo "image=$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
```

#### Fill new image id in ECS task def

```yml
- name: Fill in the new image ID in the Amazon ECS task definition
        id: task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: ${{ env.ECS_TASK_DEFINITION }}
          container-name: ${{ env.CONTAINER_NAME }}
          image: ${{ steps.push-image.outputs.image }}
          environment-variables: |
            ENVIRONMENT=${{ env.ENVIRONMENT }}
            HOST=${{ vars.HOST }} 
            PORT=${{ vars.PORT }} 
            ELASTIC_URL=${{ vars.ELASTIC_URL }} 
            ELASTIC_KEY=${{ vars.ELASTIC_KEY }}
            IMAGE=${{ steps.push-image.outputs.image }}
```

#### Deploy ECS task definitions
```yml
- name: Deploy Amazon ECS task definition
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        id: ecs-deploy
        with:
          task-definition: ${{ steps.task-def.outputs.task-definition }}
          service: ${{ env.ECS_SERVICE }}
          cluster: ${{ env.ECS_CLUSTER }}
          wait-for-service-stability: true
```

#### Check if deployment was successful
```yml
- name: Check if deployment was successful
        id: check-deployment
        run: |
            CURRENT_TASK_DEF_ARN=$(aws ecs describe-services --cluster ${{ env.ECS_CLUSTER }} --services ${{ env.ECS_SERVICE }} --query services[0].deployments[0].taskDefinition | jq -r ".")
            NEW_TASK_DEF_ARN=${{ steps.ecs-deploy.outputs.task-definition-arn }}
            REVISION=${GITHUB_SHA::8}
            echo "Current task arn: $CURRENT_TASK_DEF_ARN"
            echo "New task arn: $NEW_TASK_DEF_ARN"
            echo "Latest revision: $REVISION"
            if [ "$CURRENT_TASK_DEF_ARN" != "$NEW_TASK_DEF_ARN" ]; then
              echo "Deployment failed with latest code revision."
              exit 1
            else
              echo "Deployment successfull."
            fi
```

#### AWS CodeArtifact package versioning
- Before deploying to aws codeartifact get the latest version of the package if package already exists.

```yml
- name: Get and calculate latest package version - AWS CodeArtifact
        id: ca-getversion
        env:
          BUILD_ID: ${{ steps.prep.outputs.BUILD_ID }}
        run: |
          FLAG_INITIAL=false
          
          CURRENT_VERSION=$(aws codeartifact list-package-versions --domain $CA_DOMAIN --repository $CA_REPO --format generic --package $CA_PACKAGE --namespace $CA_NAMESPACE --query defaultDisplayVersion | jq -r ".")
          echo "current version: $CURRENT_VERSION"
          
          if [ -z "$CURRENT_VERSION" ]; then
            CURRENT_VERSION="1.0.0"
            FLAG_INITIAL=true
          fi
          IFS='.' read -ra version_parts <<< "$CURRENT_VERSION"
          MAJOR=${version_parts[0]}
          MINOR=${version_parts[1]}
          NEW_MINOR=$((MINOR + 1))
          if [ "$FLAG_INITIAL" == "true" ]; then
            NEW_MINOR="0"
          fi
          
          #version format[major.minor.build_number] 
          #build_number format{BRANCH}-${REVISION}-${TS}
          echo "latest_version=$MAJOR.$NEW_MINOR.${{ env.BUILD_ID }}" >> $GITHUB_OUTPUT
```

#### AWS CodeArtifact publishing
- Publish JAR file to aws codeartifact

```yml
- name: Publish JAR file - AWS CodeArtifact
        id: ca-deploy
        env:
          CA_VERSION: ${{ steps.ca-getversion.outputs.latest_version }}
        run: |
          export ASSET_SHA256=$(sha256sum ${{ vars.CA_SOURCE_PATH }} | awk '{print $1;}')
          #ASSET_SHA256:- This value is used as an integrity check to verify that the assetContent has not changed after it was originally sent or published.
          
          aws codeartifact publish-package-version \
          --repository $CA_REPO \
          --domain $CA_DOMAIN \
          --domain-owner $CA_DOMAIN_OWNER \
          --format generic \
          --package $CA_PACKAGE \
          --asset-content ${{ vars.CA_SOURCE_PATH }} \
          --package-version ${{ env.CA_VERSION }} \
          --asset-name $CA_PACKAGE \
          --asset-sha256 $ASSET_SHA256 \
          --namespace $CA_NAMESPACE  \
          --output text
```

#### Cancel the workflow
- To cancel/stop the current running workflow. Go to Github `Actions`, select your `workflow` and click `Cancel workflow`  