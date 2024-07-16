# OGC API Java Implementation

This is a Java implementation of varies OGC api, you can find couples of APIs seperated into modules. Only the
[server](./server) module contains the real implementation, other modules are class generated from openapi files using
swagger generator and do not expect any manual changes.

# How code generated

> WARNING: You may experience code generation problem when the code is running in Windows machine
> . Namely some enum will generated in two classes where one prefix with Api and the other emum contains
> empty skeleton.

All code generated using swagger generator with openapi file download online, these pages provide link to a website which
store the openapi file.

* [common](https://ogcapi.ogc.org/common/)
* [coverages](https://ogcapi.ogc.org/coverages/)
* [feature](https://ogcapi.ogc.org/features/)
* [maps](https://ogcapi.ogc.org/maps/)
* [processes](https://ogcapi.ogc.org/processes/)
* [records](https://ogcapi.ogc.org/records/)
* [tiles](https://ogcapi.ogc.org/tiles/)

The generator generates the interface and model only, the implementor need to implement those interface as needed.

> Noted: There are typeMappings in api generator pom to force mapping to desire class instead of enum, this is because
> some of the definition in the openapi file is too restricted and not fit for purpose. The change will not break
> the api contract.
>


Another change will be on the file replacer, this is due to the following reasons:
* javax package is removed in jdk17 and replaced by jakarta
* unused extra import that cause compilation failure
* return type ResponseEntity&lt;String&gt; is too restricted and need to be replaced by ResponseEntity&lt;?&gt;

# Implementation

The server module contains the implementation of those interfaces, for details please read the
[README](./server/README.md)

### Endpoints:

| Description         | Endpoints                              | Environment |
|---------------------|----------------------------------------|-------------|
| Logfile             | `/manage/logfile`                      | Edge        |
| Beans info          | `/manage/beans`                        | Edge        |
| Env info            | `/manage/env`                          | Edge        |
| Info (Show version) | `/manage/info`                         | Edge        |
| Health check        | `/manage/health`                       | Edge        |

# CQL
| Field | Desciption                                                                                                                                                               |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| score | The min score value of the return record, the higher the value, the more relevance it will be. By default the score isn't set, it is Elastic Search field not STAC field |



# Sort

By default the sort is sortby=-score that is desc order of _score of elastic search. You can change it to 
sortby=-score,+title or parameter as long as that field support each, ref to CQLCollectionsField.class and check 
whether the sortField is null or not. The sort function require change in elastic schema so we do not support all 
of the fields. In fact some fields sort do not make sense.

