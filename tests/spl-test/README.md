# com.ibm.streamsx.eventstore toolkit test

## Before launching the test

Install the latest streamsx package with pip, a package manager for Python, by entering the following command on the command line:

    pip install --user --upgrade streamsx


### Configure the connection to Event Store database

    export EVENTSTORE_CONNECTION=<HOST>:1100

# Run the tests with local Streams instance
```
ant test
```

# Clean-up

Delete generated files of test suites.
```
ant clean
```

### Local Streams Test

    python3 -u -m unittest test_eventstore.TestDistributed

Example for running a single test case:

    python3 -u -m unittest test_eventstore.TestDistributed.test_insert_sample


### ICP Test

    python3 -u -m unittest test_eventstore.TestICP


