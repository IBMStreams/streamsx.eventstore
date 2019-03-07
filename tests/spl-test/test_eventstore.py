import unittest

from streamsx.topology.topology import *
from streamsx.topology.tester import Tester
import streamsx.spl.op as op
import streamsx.spl.toolkit as tk
import os
import streamsx.rest as sr

import streamsx.topology.context
import requests
from urllib.parse import urlparse


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

    def setUp(self):
        Tester.setup_distributed(self)
        self._use_local_toolkit()

    def _add_toolkits(self, topo, toolkit_name):
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

        tester.test(self.test_ctxtype, cfg)
        print (str(tester.result))


    def test_insert_sample(self):
        # test the sample application
        self._build_launch_validate("test_insert_sample", "com.ibm.streamsx.eventstore.sample::InsertSampleComp", {'connectionString': self.connection}, '../../samples/EventStoreInsertSample', 100, False)


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

