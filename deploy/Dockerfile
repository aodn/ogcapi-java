ARG TF_VERSION=1.5.7

FROM hashicorp/terraform:$TF_VERSION

LABEL maintainer="devops@aodn.org.au"

ARG TG_VERSION=0.54.0
ARG USER_ID=1000
ARG GROUP_ID=1000
ARG USERNAME=appdeploy

RUN wget https://github.com/gruntwork-io/terragrunt/releases/download/v${TG_VERSION}/terragrunt_linux_amd64 \
    && mv terragrunt_linux_amd64 terragrunt \
    && chmod +x terragrunt \
    && mv terragrunt /usr/local/bin/terragrunt

RUN apk add --no-cache aws-cli

RUN echo 'alias tf="terraform"' >> /etc/profile.d/custom_aliases.sh \
    && echo 'alias tg="terragrunt"' >> /etc/profile.d/custom_aliases.sh

ENV ENV="/etc/profile"

RUN adduser -D -u $USER_ID -h /home/$USERNAME $USERNAME

USER $USERNAME:$USERNAME
