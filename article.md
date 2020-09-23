# How to make a quick'n'dirty CDC with PostgreSQL

## Overview
At `${WORK}`, I encountered multiple times some kind of architectures that heavily rely on the emission of all the events that appened to a database in order to do some stuff. 

Among multiple usage, here are the two : 
* Outbox Pattern
* Data Replication

Multiple tools exists: 
* Debezium (de facto)

# Why ? 

I wanted to understand how this kind of tool works by creating a small one using Postgres, and fs2. 

# A little bit of theory
## In general

## Postgres specific
Since v10, Postgres allow us to...


# Draft
In order to test the all things, let's use a simple `make` target to setup a Postgres instance with our current user: we forget about security, etc.

Then, another target to populate the database

Finally, another small target to simulate workloads




