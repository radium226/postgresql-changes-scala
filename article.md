---
title: How to make a quick'n'dirty CDC with PostgreSQL
date: 2020-10-16
---

# How to make a quick'n'dirty CDC with PostgreSQL

## Overview
At `${WORK}`, I encountered multiple times some technical architectures that rely on quite powerful tools that allow us to retreive all the changes that happened to a database: it's called a Change Data Capture. 

Among multiple usage, here are the three that I encountered : 
* *Data Offload*: 
* *Outbox Pattern*:

Multiple tools exists: 
* Debezium (de facto)


## What are we going to do?
In order to have a better understanding how everything works together, we're going to build a small Debezium-like library which will listen to PostgreSQL changes in Scala, using `fs2` and `scodec` in a `cats`-friendly way. 


## But wait... How does it work?

### A little bit of theory, first...
#### In general



#### Postgres specific
Since v10, Postgres allow us to...


## Let's play with PostgreSQL
In order to test the all things, let's use a simple `make` target to setup a PostgreSQL instance with our current user: we forget about security, etc.

Then, another target to populate the database

Finally, another small target to simulate workloads

## The `CaptureSpec` abstract class
In order to 

## Protocol decoding with the `scodec` library

## Mapping using the `TupleDataReader` class and the `shapeless` library

## Embedding everything under an `fs2`'s `Pipe`s
