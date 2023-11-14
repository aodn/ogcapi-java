## CI/CD Steps

```mermaid
%%{
  init: {
    'theme': 'base',
    'themeVariables': {
      'edgeLabelBackground': '#ffffff',
      'tertiaryTextColor': '#0f00aa',
      'clusterBkg': '#fafaff',
      'clusterBorder': '#0f00aa'
    }
  }
}%%

graph TD
    subgraph Build
        1(Build Docker container)
        2(Scan container)
        3(Push to Dev ECR)

    end

    subgraph Deploy-Development
        4(Deploy to dev ECS)
        5(Manual review)
    end

    subgraph Deploy-Staging
        6(Push to Staging ECR)
        7(Deploy to staging ECS)
        8(Manual review)
    end

    subgraph Deploy-Production
        9(Push to Prod ECR)
        10(Deploy to prod ECS)
    end

    1 --> 2
    2 --> 3
    3 --> 4
    4 --> 5
    5 --> 6
    6 --> 7
    7 --> 8
    8 --> 9
    9 --> 10



## CI/CD Pipeline

### Continuous Integration (CI) / Continuous Deployment (CD) Pipeline

This project includes a CI/CD pipeline for automating the build and deployment process. The pipeline includes the following steps:

1. **Build the Docker container:**
   - Automated step to build the Docker container whenever changes are pushed to the repository.

2. **Scan the container (Optional):**
   - Optional automated step to scan the Docker container for security vulnerabilities.

3. **Push the container to AWS ECR for Development:**
   - Automated step to push the Docker container to the Amazon Elastic Container Registry (ECR) for the development environment.

4. **Deploy to development ECS:**
   - Automated deployment of the container to the development Elastic Container Service (ECS).

5. **Manual review process:**
   - Manual review step after the deployment to the development environment.

6. **Push the container to AWS ECR for Staging:**
   - Automated step to push the Docker container to the ECR for the staging environment.

7. **Deploy to staging ECS:**
   - Automated deployment of the container to the staging ECS.

8. **Manual review process:**
   - Manual review step after the deployment to the staging environment.

9. **Push the container to AWS ECR for Production:**
   - Automated step to push the Docker container to the ECR for the production environment.

10. **Deploy to production ECS (only from the main branch):**
    - Automated deployment of the container to the production ECS, restricted to changes made to the main branch.
