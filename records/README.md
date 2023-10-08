# OGC Records API

The open api file download from [here](https://github.com/opengeospatial/ogcapi-records/tree/master/core/openapi), 
as of 8 Oct 2023, this is still in draft mode.

The reason we keep it because I want to know the similarity of the generated class in Records/CollectionInfo vs
Features/Collection. It ends up they are almost the same and only different in itemType where we can set it.

So in short there is nothing interesting for the open api module.

## Extended functions
The Record api pretty much reuse the features api [details](https://github.com/opengeospatial/ogcapi-records),
by extend the function with more parameters. We cannot do it directly without changing the openapi document which
is bad. 

So as a workaround we use the @Hidden to hide some endpoints in features and manually re-create the endpoints 