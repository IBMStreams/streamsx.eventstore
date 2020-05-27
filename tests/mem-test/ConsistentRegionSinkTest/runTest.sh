#!/bin/bash
# Copyright (C)2020, International Business Machines Corporation
# All rights reserved.


cmd="../scripts/runTestMem.pl --op=EventStoreSink --main=com.ibm.streamsx.eventstore.test::CrInsertTest --iterations=10 --interval=60"
echo "$cmd"
eval $cmd

