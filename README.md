[![semantic-release](https://img.shields.io/badge/semantic-release-e10079.svg?logo=semantic-release)](https://github.com/semantic-release/semantic-release)

# lsd-distributed-mongodb-access

![GitHub](https://img.shields.io/github/license/lsd-consulting/lsd-distributed-mongodb-access)
![Codecov](https://img.shields.io/codecov/c/github/lsd-consulting/lsd-distributed-mongodb-access)

[![CI](https://github.com/lsd-consulting/lsd-distributed-mongodb-access/actions/workflows/ci.yml/badge.svg)](https://github.com/lsd-consulting/lsd-distributed-mongodb-access/actions/workflows/ci.yml)
[![Nightly Build](https://github.com/lsd-consulting/lsd-distributed-mongodb-access/actions/workflows/nightly.yml/badge.svg)](https://github.com/lsd-consulting/lsd-distributed-mongodb-access/actions/workflows/nightly.yml)
[![GitHub release](https://img.shields.io/github/release/lsd-consulting/lsd-distributed-mongodb-access)](https://github.com/lsd-consulting/lsd-distributed-mongodb-access/releases)
![Maven Central](https://img.shields.io/maven-central/v/io.github.lsd-consulting/lsd-distributed-mongodb-access)

## About

This is a MongoDB version of the data access for the distributed data storage.

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
