import unittest

from streamsx.topology.topology import *
from streamsx.topology.tester import Tester
from streamsx.topology.state import ConsistentRegionConfig
import streamsx.spl.op as op
import streamsx.spl.toolkit as tk
import streamsx.rest as sr
import streamsx.spl.types as spltypes
import streamsx.eventstore as es

import os
import subprocess

import streamsx.topology.context
import requests
from urllib.parse import urlparse


def streams_install_env_var():
    result = True
    try:
        os.environ['STREAMS_INSTALL']
    except KeyError: 
        result = False
    return result

class TestDistributed(unittest.TestCase):
    """ Test invocations of composite operators in local IBM Streams instance """

    def _use_local_toolkit(self):
        if os.environ.get('STREAMSX_EVENTSTORE_TOOLKIT') is None:
            self.eventstore_toolkit_location = '../../com.ibm.streamsx.eventstore'
        else:
            self.eventstore_toolkit_location = os.environ.get('STREAMSX_EVENTSTORE_TOOLKIT')        

    @classmethod
    def setUpClass(self):
        self.connection = os.environ['EVENTSTORE_CONNECTION']
        self.database = os.environ['EVENTSTORE_DB']

    def setUp(self):
        Tester.setup_distributed(self)
        self._use_local_toolkit()

    def _add_toolkits(self, topo, toolkit_name):
        if toolkit_name is not None:
            tk.add_toolkit(topo, toolkit_name)
        if self.eventstore_toolkit_location is not None:
            tk.add_toolkit(topo, self.eventstore_toolkit_location)

    def _build_launch_validate(self, name, composite_name, parameters, toolkit_name, num_tuples, exact):
        print ("------ "+name+" ------")        
        topo = Topology(name)
        self._add_toolkits(topo, toolkit_name)
	
        params = parameters
        # Call the test composite
        test_op = op.Source(topo, composite_name, 'tuple<rstring result>', params=params)

        tester = Tester(topo)
        tester.tuple_count(test_op.stream, num_tuples, exact=exact)

        cfg = {}
        if ("TestICP" in str(self)):
            cfg = self._service()

        # change trace level
        job_config = streamsx.topology.context.JobConfig(tracing='info')
        job_config.add(cfg)

        cfg[streamsx.topology.context.ConfigParams.SSL_VERIFY] = False

        tester.test(self.test_ctxtype, cfg, always_collect_logs=True)
        print (str(tester.result))

    def _build_only(self, name, topo):
        result = streamsx.topology.context.submit("TOOLKIT", topo.graph) # creates tk* directory
        print(name + ' (TOOLKIT):' + str(result))
        assert(result.return_code == 0)
        result = streamsx.topology.context.submit("BUNDLE", topo.graph)  # creates sab file
        print(name + ' (BUNDLE):' + str(result))
        assert(result.return_code == 0)

    def _index_tk(self, tkdir):
        si = os.environ['STREAMS_INSTALL']
        this_dir = os.path.dirname(os.path.realpath(__file__))
        tkl = this_dir+'/'+tkdir
        ri = subprocess.call([os.path.join(si, 'bin', 'spl-make-toolkit'), '-i', tkl])

    def test_insert_sample_flush_remaining_tuples(self):
        print ('\n---------'+str(self))
        name = 'test_insert_sample_flush_remaining_tuples'
        # test the sample application
        # final marker should flush the remaining tuples
        num_expected = 305
        batch_size = 50
        self._build_launch_validate(name, "com.ibm.streamsx.eventstore.sample::InsertSampleComp", {'connectionString': self.connection, 'databaseName': self.database, 'batchSize':batch_size, 'iterations': num_expected}, '../../samples/EventStoreInsertSample', num_expected, True)

    def test_insert_sample_batch_complete(self):
        print ('\n---------'+str(self))
        name = 'test_insert_sample_batch_complete'
        # test the sample application
        # final marker received after last async batch is triggered
        num_expected = 300
        batch_size = 50
        self._build_launch_validate(name, "com.ibm.streamsx.eventstore.sample::InsertSampleComp", {'connectionString': self.connection, 'databaseName': self.database, 'batchSize':batch_size, 'iterations': num_expected}, '../../samples/EventStoreInsertSample', num_expected, True)


    def test_insert_consistent_region(self):
        print ('\n---------'+str(self))
        name = 'test_insert_consistent_region'
        topo = Topology(name)
        self._add_toolkits(topo, None)
        # configuration of consistent region trigger period
        trigger_period = 10
        num_expected_tuples = 8000
        num_resets = 2
        run_for = 120 # in seconds

        beacon = op.Source(topo, "spl.utility::Beacon",
            'tuple<int64 id, rstring val>',
            params = {'period': 0.01, 'iterations': num_expected_tuples})
        beacon.id = beacon.output('(int64)IterationCount()')
        beacon.val = beacon.output(spltypes.rstring('CR_TEST'))
        beacon.stream.set_consistent(ConsistentRegionConfig.periodic(trigger_period))
        
        es.insert(beacon.stream, self.connection, self.database, 'CRTable', primary_key='id', front_end_connection_flag=True)
        
        #self._build_only(name, topo)

        tester = Tester(topo)
        tester.run_for(run_for)
        tester.resets(num_resets) # minimum number of resets for each region

        cfg = {}
        if ("TestICP" in str(self)):
            cfg = self._service()

        # change trace level
        job_config = streamsx.topology.context.JobConfig(tracing='warn')
        job_config.add(cfg)

        cfg[streamsx.topology.context.ConfigParams.SSL_VERIFY] = False

        tester.test(self.test_ctxtype, cfg, always_collect_logs=True)
        print (str(tester.result))

    @unittest.skipIf(streams_install_env_var() == False, "Missing STREAMS_INSTALL environment variable.")
    def test_compile_time_error_checkpoint_periodic(self):
        print ('\n---------'+str(self))
        if self.eventstore_toolkit_location is not None:
            test_toolkit = 'compile.test'
            self._index_tk(test_toolkit)
            # compile test only
            r = op.main_composite(kind='com.ibm.streamsx.eventstore.test::Test_checkpoint_periodic', toolkits=[self.eventstore_toolkit_location, test_toolkit])
            rc = streamsx.topology.context.submit('BUNDLE', r[0])
            #expect compile error
            self.assertEqual(1, rc['return_code'])

    @unittest.skipIf(streams_install_env_var() == False, "Missing STREAMS_INSTALL environment variable.")
    def test_compile_time_error_checkpoint_operator_driven(self):
        print ('\n---------'+str(self))
        if self.eventstore_toolkit_location is not None:
            test_toolkit = 'compile.test'
            self._index_tk(test_toolkit)
            # compile test only
            r = op.main_composite(kind='com.ibm.streamsx.eventstore.test::Test_checkpoint_operatorDriven', toolkits=[self.eventstore_toolkit_location, test_toolkit])
            rc = streamsx.topology.context.submit('BUNDLE', r[0])
            #expect compile error
            self.assertEqual(1, rc['return_code'])

    @unittest.skipIf(streams_install_env_var() == False, "Missing STREAMS_INSTALL environment variable.")
    def test_compile_time_error_consistent_region_unsupported_configuration(self):
        print ('\n---------'+str(self))
        if self.eventstore_toolkit_location is not None:
            test_toolkit = 'compile.test'
            self._index_tk(test_toolkit)
            # compile test only
            r = op.main_composite(kind='com.ibm.streamsx.eventstore.test::Test_consistent_region_unsupported_configuration', toolkits=[self.eventstore_toolkit_location, test_toolkit])
            rc = streamsx.topology.context.submit('BUNDLE', r[0])
            #expect compile error
            self.assertEqual(1, rc['return_code'])



class TestICP(TestDistributed):
    """ Test invocations of composite operators in remote Streams instance using local toolkit """

    @classmethod
    def setUpClass(self):
        env_chk = True
        try:
            print("STREAMS_REST_URL="+str(os.environ['STREAMS_REST_URL']))
        except KeyError:
            env_chk = False
        assert env_chk, "STREAMS_REST_URL environment variable must be set"
        super().setUpClass()    

    def setUp(self):
        Tester.setup_distributed(self)
        self._use_local_toolkit()       

    def _service (self, force_remote_build = True):
        auth_host = os.environ['AUTH_HOST']
        auth_user = os.environ['AUTH_USERNAME']
        auth_password = os.environ['AUTH_PASSWORD']
        streams_rest_url = os.environ['STREAMS_REST_URL']
        streams_service_name = os.environ['STREAMS_SERVICE_NAME']
        streams_build_service_port = os.environ['STREAMS_BUILD_SERVICE_PORT']
        uri_parsed = urlparse (streams_rest_url)
        streams_build_service = uri_parsed.hostname + ':' + streams_build_service_port
        streams_rest_service = uri_parsed.netloc
        r = requests.get ('https://' + auth_host + '/v1/preauth/validateAuth', auth=(auth_user, auth_password), verify=False)
        token = r.json()['accessToken']
        cfg = {
            'type': 'streams',
            'connection_info': {
                'serviceBuildEndpoint': 'https://' + streams_build_service,
                'serviceRestEndpoint': 'https://' + streams_rest_service + '/streams/rest/instances/' + streams_service_name
            },
            'service_token': token
        }
        cfg [streamsx.topology.context.ConfigParams.FORCE_REMOTE_BUILD] = force_remote_build
        return cfg


class TestICPRemote(TestICP):
    """ Test invocations of composite operators in remote Streams instance using remote toolkit from build service"""

    @classmethod
    def setUpClass(self):
        super().setUpClass()

    def setUp(self):
        Tester.setup_distributed(self)
        self.eventstore_toolkit_location = None

