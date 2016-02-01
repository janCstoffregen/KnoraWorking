#!/usr/bin/env bash

/run.sh &

sleep 5

cd /builds/salsah-suite/rapier-scala

./webapi/scripts/sesame-ci-prepare.sh

cd /builds/salsah-suite/rapier-scala

sbt "project webapi" "sesame:test"
