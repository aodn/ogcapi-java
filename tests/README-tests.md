# Docker Image Structural Testing
The configuration file `test/config.yaml` defines various "structural" checks to perform on the docker image created by `../Dockerfile`.

The test require the use of the [container-structure-test](https://github.com/GoogleContainerTools/container-structure-test) from Google.

Follow their documentation to install.

## Usage
Generate your docker image using your preferred method e.g.
```shell
docker build -t myimage .
```

To run the tests against the image, run the following:
```shell
container-structure-test test --image myimage --config tests/config.yaml
```
