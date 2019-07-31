import unittest

from streamsx.topology.topology import *
from streamsx.topology.tester import Tester
from streamsx.topology.state import ConsistentRegionConfig
from streamsx.topology.schema import CommonSchema, StreamSchema
import streamsx.spl.op as op
import streamsx.spl.toolkit as tk
import streamsx.rest as sr
import streamsx.spl.types as spltypes
import streamsx.eventstore as es

import os
import subprocess
from subprocess import call, Popen, PIPE

import streamsx.topology.context


def streams_install_env_var():
    result = True
    try:
        os.environ['STREAMS_INSTALL']
    except KeyError: 
        result = False
    return result

class Test(unittest.TestCase):
    """ Test build of apps with local IBM Streams instance """

    def _use_local_toolkit(self):
        if os.environ.get('STREAMSX_EVENTSTORE_TOOLKIT') is None:
            self.eventstore_toolkit_location = '../../com.ibm.streamsx.eventstore'
        else:
            self.eventstore_toolkit_location = os.environ.get('STREAMSX_EVENTSTORE_TOOLKIT')
        # location of the samples      
        if os.environ.get('STREAMSX_EVENTSTORE_SAMPLES') is None:
            self.samples_location = '../../samples/EventStoreInsertSample'
        else:
            self.samples_location = os.environ.get('STREAMSX_EVENTSTORE_SAMPLES')


    def setUp(self):
        Tester.setup_distributed(self)
        self._use_local_toolkit()

    def _add_toolkits(self, topo, toolkit_name):
        if toolkit_name is not None:
            tk.add_toolkit(topo, toolkit_name)
        if self.eventstore_toolkit_location is not None:
            tk.add_toolkit(topo, self.eventstore_toolkit_location)


    def _build_only(self, name, topo):
        result = streamsx.topology.context.submit("TOOLKIT", topo.graph) # creates tk* directory
        print(name + ' (TOOLKIT):' + str(result))
        assert(result.return_code == 0)
        result = streamsx.topology.context.submit("BUNDLE", topo.graph)  # creates sab file
        print(name + ' (BUNDLE):' + str(result))
        assert(result.return_code == 0)

    def _index_tk(self, tkdir):
        si = os.environ['STREAMS_INSTALL']
        if os.path.isabs(tkdir) is False:
            this_dir = os.path.dirname(os.path.realpath(__file__))
            tkl = this_dir+'/'+tkdir
        ri = subprocess.call([os.path.join(si, 'bin', 'spl-make-toolkit'), '-i', tkl])


    def _run_shell_command_line(self, command):
        process = Popen(command, universal_newlines=True, shell=True, stdout=PIPE, stderr=PIPE)
        stdout, stderr = process.communicate()
        return stdout, stderr, process.returncode


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



