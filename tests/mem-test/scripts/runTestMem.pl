#!/usr/bin/perl

# Copyright (C)2016, International Business Machines Corporation
# All rights reserved.                             


# check memory consumption of the PE

use strict;
use warnings;
use Data::Dumper;
use Cwd 'abs_path';
use Getopt::Long;

use File::Basename;
use Cwd qw(abs_path cwd);
my $dirname = dirname(abs_path($0));
my $CommonINC = abs_path(dirname(abs_path(__FILE__)) . '/Common/'); 
unshift @INC, $CommonINC;
require SimpleDom;

my $STREAMS_INSTALL=$ENV{STREAMS_INSTALL};
$ENV{STREAMS_INSTALL} or die "Internal Error: STREAMS_INSTALL not set. Please source streamsprofile.sh";

my $STREAMS_BIN=$STREAMS_INSTALL."/bin";
my $STREAMTOOL_CMD=$STREAMS_BIN."/streamtool";

my $xmlFile = "/tmp/st.$$";
my $jobIdFile = "/tmp/stjid.$$";
my $memFile = "mem.$$";

my $version;
my $jobid;
my $pecPid;
my $useMetrics=1; # either top (0) or metrics (1) are used to determine the mem consumption
my $initialCaptureInterval=2;
my $nIterations=15;
my $sleepTime=10;
my $compName="Main"; # main composite name
my $opName="CustomSink"; # operator name to retrieve the nTuplesProcessed metric

sub getStreamsVersion()
{
	return substr(`$STREAMTOOL_CMD version | grep Version`, 8, 3,);
}

# Internal method to run a system command and capture the output
sub _runCmdNoDieCapture($;$) { # logs starting and completion; returns exit code (processed $?)
    my ($cmd, $captureStdErr) = @_;

    $captureStdErr = 1 if !defined($captureStdErr);

    my $ec;
    my $output = $captureStdErr == 1 ? `$cmd 2>&1` : `$cmd`; # Redirect stderr to stdout so we can capture it
    if ($? < 0) { # cmd didn't run
        $ec = -1;
    } else {
        # return exit code... -1 if it died.
        my $low = $? & 0xff; # non-0 if died
        my $hi = $? >> 8; # exit code if exit() was called
        $ec = $low ? -1 : $hi;
    }

    return ($ec, $output);
}

sub captureStats() {
        my $res;
	if ($version >= 4.2) {
		system($STREAMTOOL_CMD." capturestate --jobs $jobid --select jobs=metrics -f $xmlFile");
	} else {
		my $cmd = $STREAMTOOL_CMD." capturestate --select jobs=metrics --jobs $jobid";
		#print $cmd."\n";
		my ($rc, $output) = _runCmdNoDieCapture($cmd);
		dumpToFile($xmlFile, $output);
	}

	# load xml file
	my $metrics = new SimpleDom($xmlFile);
	unlink $xmlFile;

	# collect metrics from pes/operators
	my $pes = $metrics->getElementsByPath("/instance/job/pe");
	foreach my $pe (@$pes)
	{
		my ($peId,$processId) = $pe->getAttributeValues("id","processId");
		my $cpu = $pe->getElementsByPath('./metric[@name=nCpuMilliseconds]/metricValue')->[0]->getAttribute("value");
		my $mem = $pe->getElementsByPath('./metric[@name=nResidentMemoryConsumption]/metricValue')->[0]->getAttribute("value");
		eval {
			my $nTuplesProcessed = $pe->getElementsByPath('./operator[@name='.$opName.']/inputPort/metric[@name=nTuplesProcessed]/metricValue')->[0]->getAttribute("value");
			print "pe:$peId pid:$processId cpu:$cpu mem:$mem nTuplesProcessed:$nTuplesProcessed \n";
			$res = $mem;	
		}
	}
	return $res."\n";
}

sub getPID() {
	my $res;
	my $cmd = $STREAMTOOL_CMD." lspe --jobs ".$jobid." --xheaders | awk '{print \$6}'";
	$res = `$cmd`;
	chomp($res);
	print "pid=$res\n";
	return $res;
}

sub captureResMem($) {
	my ($pid) = @_;
	my $res;
	my $cmd = "top -b -n 1 -p $pid |tail -n 2 | awk '{print \$6}'";
	$res = `$cmd`;
	chomp($res);
	print "\n$res";
	return $res;
}


sub dumpToFile($$) {
	my ($file, $data) = @_;
	open FH, ">>$file" or die ("ERR_FILE_OPEN_ERROR",$file,$!);
	print FH $data;
	close(FH);
}

sub readJobIdFile() {
	open FILE, "<$jobIdFile";
	$jobid = do { local $/; <FILE> };
	chomp($jobid);
	#print "jobid=".$jobid."\n";
}

sub submitJob() {
	my $executeRC;
	my $fileExt = "sab";
	$fileExt = "adl" if ($version < 4);
	my $cmd = $STREAMTOOL_CMD." submitjob output/$compName.".$fileExt." --outfile $jobIdFile";
	my $rc = `$cmd`;
	print "$rc\n";
	$executeRC = $?;
	die "ERROR: Job submission failed" if (0 != $executeRC);
	readJobIdFile();
}

sub cancelJob() {
	system($STREAMTOOL_CMD." canceljob --file $jobIdFile");
	unlink $jobIdFile;
}

my $needHelp;
my $iterationsParam;
my $intervalParam;
my $mainParam;
my $opParam;

GetOptions ("iterations=s" => \$iterationsParam,
        "interval=s", \$intervalParam,
        "main=s",\$mainParam,
        "op=s",\$opParam,
        "help|h|?",\$needHelp,
        ) or usage(1);

if ($needHelp) {
	usage(0);
}

if (defined $iterationsParam) {
	$nIterations = $iterationsParam;
}
if (defined $intervalParam) {
	$sleepTime = $intervalParam;
}
if (defined $mainParam) {
	$compName = $mainParam;
	$compName =~s/::/./ig;
}
if (defined $opParam) {
	$opName = $opParam;
}

$version = getStreamsVersion();

# submit the job
submitJob();

sleep(30);

$pecPid = getPID();

my $memoryConsumption;
my $filename="mem0";
unlink $filename;
my $i = 0;
for($i = 0; $i < $nIterations; $i++) {
	$memoryConsumption = ($useMetrics ? captureStats() : captureResMem($pecPid));
	dumpToFile($memFile,$memoryConsumption);
	dumpToFile($filename,$memoryConsumption) if ($initialCaptureInterval == $i);
	sleep($sleepTime);
}

# write file to compare it with other dump file
$filename="mem1";
unlink $filename;
$memoryConsumption = ($useMetrics ? captureStats() : captureResMem($pecPid));
dumpToFile($filename,$memoryConsumption);
dumpToFile($memFile,$memoryConsumption);

# collect metrics to show how many tuples are processed
captureStats();

# cancel the job
cancelJob();






