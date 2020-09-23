PGDATA := postgres

# https://github.com/postgres/postgres/blob/2b27273435392d1606f0ffc95d73a439a457f08e/src/backend/replication/pgoutput/pgoutput.c#L123
$(PGDATA)/PG_VERSION:
	initdb \
		-D "$(PGDATA)" \
		-A "trust" \
		-U "postgres"

.PHONY: pg-start
pg-start: $(PGDATA)/PG_VERSION
	postgres \
		-D "$(PGDATA)" \
		-c unix_socket_directories="$(PWD)" \
		-c wal_level="logical" \
		-c max_replication_slots=1 

.PHONY: pg-clean
pg-clean:
	rm -Rf "$(PGDATA)"

.PHONY: pg-psql
pg-psql:
	psql \
		-U "postgres" \
		-h "localhost" \
		-p 5432

pg-init:
	psql \
		-U "postgres" \
		-h "localhost" \
		-p 5432 \
		-f "init.sql"

# TODO: Talk about the order of the parameters

.PHONY: pg-test
pg-test:
	pg_recvlogical \
		-d "postgres" \
		-U "postgres" \
		-h "localhost" \
		-p 5432 \
		--slot=my_slot \
		--file=- \
		--no-loop \
		--option=proto_version=1 \
		--option=publication_names=my_publication \
		--plugin=pgoutput \
		--start
