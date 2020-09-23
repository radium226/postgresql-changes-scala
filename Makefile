#!/usr/bin/env make

SHELL = /usr/bin/env
.SHELLFLAGS = bash -eu -c

.ONESHELL:

# https://www.postgresql.org/docs/12/logicaldecoding-example.html
# https://medium.com/@film42/getting-postgres-logical-replication-changes-using-pgoutput-plugin-b752e57bfd58
# https://github.com/debezium/postgres-decoderbufs/blob/master/README.md
# vendors/debezium/debezium-connector-postgres/src/main/java/io/debezium/connector/postgresql/connection/pgoutput/PgOutputMessageDecoder.java

include make/pg.mk

.PHONY: insert-random-persons
insert-random-persons:
	while sleep 1; do
		psql <<EOSQL \
			-U "postgres" \
			-h "localhost" \
			-p 5432 \
			-v name="'$$( shuf -n1 "persons.txt" )'"
	INSERT INTO persons(firstName, lastName) VALUES (:name, :name)
	EOSQL
	done

.PHONY: clean
clean: pg-clean
