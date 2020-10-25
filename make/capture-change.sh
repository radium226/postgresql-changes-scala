#!/bin/bash

set -euo pipefail
set -m # Enable Jobs

serve()
{
    echo " --> Serve" 2>"/dev/null"
    sleep 10
    echo " --> Serve!" 2>"/dev/null"
}

wait_for()
{
    sleep 1
}

execute()
{
    echo " --> Execute" 2>"/dev/null"
    #declare sql="${1}"
    sleep 1
    echo " --> Execute! " 2>"/dev/null"
}

receive()
{
    echo " --> Receive" 2>"/dev/null"
    sleep 10
    echo " --> Receive! " 2>"/dev/null"
}



main()
{
    serve &
    declare server_jid=$!

    wait_for

    {
        receive >"OUTPUT.BIN"
    } &
    declare receiver_jid=$!

    execute 

    kill -- -${receiver_jid}
    kill -- -${server_jid}

    sleep 0.125

    jobs
}

main "${@}"

