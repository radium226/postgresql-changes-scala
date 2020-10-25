#!/usr/bin/env python

from sys import argv
from subprocess import Popen, PIPE, call
from collections import namedtuple

from contextlib import contextmanager
from pathlib import Path
from time import sleep

from argparse import ArgumentParser

Config = namedtuple("Config", [
    "data_directory_path", 
    "port", 
    "user", 
    "host", 
    "database"
])

def parse_variable(text):
    [key, value] = text.split("=")
    return (key, value)


def parse_variables(items):
    """
    Parse a series of key-value pairs and return a dictionary
    """
    d = {}

    if items:
        for item in items:
            key, value = parse_variable(item)
            d[key] = value
    return d

class Postgres:

    def __init__(self, config):
        self._config = config

    @property
    def connection_args(self):
        return [
            "-U", self._config.user, 
            "-d", self._config.database,
            "-h", self._config.host,
            "-p", str(self._config.port)
        ]

    @contextmanager
    def server(self):
        process = Popen([
            "postgres", 
            "-D", str(self._config.data_directory_path), 
            "-c", f"unix_socket_directories=.",
            "-c", f"wal_level=logical",
            "-c", "max_replication_slots=1"
        ])
        try:
            while call(["pg_isready"] + self.connection_args) != 0:
                print("Waiting for PostgreSQL to be ready... ")
                sleep(1)
            yield
        finally:
            process.terminate()
            process.wait()


    def execute(self, query, **variables):
        pgsql_variable_args = [variable_arg for variable_args in [["-v", f"{name}='{value}'"] for name, value in variables.items()] for variable_arg in variable_args]
        pgsql_args = self.connection_args + pgsql_variable_args
        pgsql_command = ["psql"] + pgsql_args
        print(pgsql_command)
        pgsql_process = Popen(pgsql_command, stdin=PIPE)
        pgsql_process.stdin.write(f"{query}; \n".encode("utf-8"))
        pgsql_process.stdin.close()
        pgsql_process.wait()


    @contextmanager
    def capture(self, slot, publication_name, change_file_path):
        pg_recvlogical_replication_args = [
            f"--slot={slot}",
            f"--file={str(change_file_path)}",
            "--no-loop",
            "--option=proto_version=1",
            f"--option=publication_names={publication_name}",
            "--plugin=pgoutput",
            "--start"
        ]
        pg_recvlogical_args = self.connection_args + pg_recvlogical_replication_args
        pg_recvlogical_command = ["pg_recvlogical"] + pg_recvlogical_args
        print(pg_recvlogical_command)
        pg_recvlogical_process = Popen(pg_recvlogical_command)
        # Just wait a little
        sleep(1)
        try:
            yield
        finally:
            pg_recvlogical_process.terminate()
            pg_recvlogical_process.wait()

def main(config, slot, publication_name, change_file_path, query, variables):
    postgres = Postgres(config)
    with postgres.server():
        with postgres.capture(slot, publication_name, change_file_path):
                postgres.execute(query, **variables)
                sleep(1)



if __name__ == "__main__":
    argument_parser = ArgumentParser(add_help=False)
    argument_parser.add_argument("-D", dest="data_directory", metavar="PGDATA", help="Data directory", default="postgres")
    argument_parser.add_argument("-U", dest="user", metavar="USER", help="User", default="postgres")
    argument_parser.add_argument("-d", dest="database", metavar="DBNAME", help="Database", default="postgres")
    argument_parser.add_argument("-h", dest="host", metavar="HOSTNAME", help="Host", default="localhost")
    argument_parser.add_argument("-p", dest="port", metavar="PORT", help="Port", default=5432)
    argument_parser.add_argument("--slot", metavar="SLOT", help="Number of slots", default="my_slot")
    argument_parser.add_argument("--publication-name", metavar="PUBLICATION_NAME", help="Publication names", default="my_publication")
    argument_parser.add_argument("--change-file", dest="change_file", metavar="CHANGE_FILE", help="Change file", default="pgoutput.bin")
    argument_parser.add_argument("-v", dest="variables", metavar="KEY=VALUE", action="append", help="Query variables")
    argument_parser.add_argument("query", help="Query")
    
    args = argument_parser.parse_args()
    print(args)
    
    config = Config(
        data_directory_path=Path(args.data_directory),
        database=args.database,
        user=args.user,
        host=args.host, 
        port=args.port,
    )

    def parse_variables(variables_to_parse):
        parsed_variables = {}
        for variable in variables_to_parse:
            segments = variable.split("=")
            name = segments[0]
            value = segments[1]
            parsed_variables.update({ name: value })
        return parsed_variables

    variables = parse_variables(args.variables)
    print(variables)
    
    main(config, args.slot, args.publication_name, Path(args.change_file), args.query, variables)