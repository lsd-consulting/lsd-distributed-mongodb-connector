[![semantic-release](https://img.shields.io/badge/semantic-release-e10079.svg?logo=semantic-release)](https://github.com/semantic-release/semantic-release)

# lsd-distributed-mongodb-connector

![GitHub](https://img.shields.io/github/license/lsd-consulting/lsd-distributed-mongodb-connector)
![Codecov](https://img.shields.io/codecov/c/github/lsd-consulting/lsd-distributed-mongodb-connector)

[![CI](https://github.com/lsd-consulting/lsd-distributed-mongodb-connector/actions/workflows/ci.yml/badge.svg)](https://github.com/lsd-consulting/lsd-distributed-mongodb-connector/actions/workflows/ci.yml)
[![Nightly Build](https://github.com/lsd-consulting/lsd-distributed-mongodb-connector/actions/workflows/nightly.yml/badge.svg)](https://github.com/lsd-consulting/lsd-distributed-mongodb-connector/actions/workflows/nightly.yml)
[![GitHub release](https://img.shields.io/github/release/lsd-consulting/lsd-distributed-mongodb-connector)](https://github.com/lsd-consulting/lsd-distributed-mongodb-connector/releases)
![Maven Central](https://img.shields.io/maven-central/v/io.github.lsd-consulting/lsd-distributed-mongodb-connector)

## About

This is a MongoDB version of the data connector for the distributed data storage.

## Modes

To cater for various usage scenarios, the connector can be initialised in two modes based on the values of
the `failOnConnectionError` parameter:

- false - if the connector fails to connect on start up, it will silently swallow the exception and set the connector to
  the `inactive` mode.
  This means all queries for storing interactions will be ignored. This is the default behaviour and should be used when
  the main role of the connector is to capture interactions.
- true - if the connector fails to connect on start up, it will throw an exception preventing the startup of the
  application.
  This is a useful mode for applications that rely on the connector for their critical functionality, eg.
  the `lsd-dostributed-generator-ui`.

## Properties

The following properties can be overridden by setting a System property.

| Property Name                             | Default | Description                                                                                                                                                                                |
|-------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| lsd.dist.db.trustStoreLocation            | empty   | Useful for connecting to a db instance requiring a certificate of the signing authority (only required for TLS where the certificate is provided).                                         |
| lsd.dist.db.trustStorePassword            | empty   | Useful for connecting to a db instance requiring a certificate of the signing authority.                                                                                                   |
| lsd.dist.db.connectionTimeout.millis      | 500     | This value should be higher than the default (the MongoDB driver's default is 20,000) when `failOnConnectionError=false`.                                                                  |
| lsd.dist.db.collectionSizeLimit.megabytes | 10,000  | The connector creates the LSD collection as a capped size collection when it's missing with some predefined settings, eg. required indexes, capped size. This values sets the capped size. |
| lsd.dist.db.failOnConnectionError         | false   | See [Modes](#Modes) for details.                                                                                                                                                           |

## Releasing

Pushing to main builds but does not release. Maven Central limits the
io.github.lsd-consulting namespace to 7 releases a month, so each release is
started on purpose. semantic-release still picks the version from the feat/fix
commits since the last release, and does nothing if there are none.

Release by hand:

```
gh workflow run release.yml -R lsd-consulting/lsd-distributed-mongodb-connector -f reason="why this release is needed"
```

To release weekly instead (Tuesday 07:00 UTC), set the repository variable
`WEEKLY_RELEASE` to `true`. It is off by default.
