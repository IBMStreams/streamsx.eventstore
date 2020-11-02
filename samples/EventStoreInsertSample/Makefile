# Copyright (C) 2019, International Business Machines Corporation. 
# All Rights Reserved.

.PHONY: build all clean distributed

SPLC_FLAGS = -a
OUTPUT_DIR = output
ifeq ($(STREAMS_STUDIO_BUILDING), 1)
  # Building from Streams Studio, use env vars set by studio
  SPLC = $(STREAMS_STUDIO_SC_PATH)
  SPL_PATH = $(STREAMS_STUDIO_SPL_PATH)
  OUTPUT_DIR = $(STREAMS_STUDIO_OUTPUT_DIRECTORY)
else ifdef STREAMS_BUILDSERVICE
    $(info Building using buildservice)
    ifeq ($(STREAMS_INSTALL),)
        $(error error: environment variable STREAMS_INSTALL has to be set)
    endif
    SPLC = $(STREAMS_INSTALL)/bin/sc
    SPL_PATH = ../toolkits:$(STREAMS_INSTALL)/toolkits
else
  # Building outside of Streams Studio...
  ifeq ($(STREAMS_INSTALL),)
    $(error error: environment variable STREAMS_INSTALL has to be set)
  endif
  # Set the STREAMSX_EVENTSTORE_TOOLKIT variable if streamsx.eventstore sample or toolkit location changed in your environment.
  ifeq ($(STREAMSX_EVENTSTORE_TOOLKIT),)
    ifneq ($(realpath ../../com.ibm.streamsx.eventstore/toolkit.xml),)
      STREAMSX_EVENTSTORE_TOOLKIT = ../../com.ibm.streamsx.eventstore
    endif
    ifeq ($(STREAMSX_EVENTSTORE_TOOLKIT),)
      ifneq ($(realpath $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.eventstore),)
        STREAMSX_EVENTSTORE_TOOLKIT = $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.eventstore
      endif
    endif
  endif
  SPLC = $(STREAMS_INSTALL)/bin/sc
  SPL_PATH = $(STREAMSX_EVENTSTORE_TOOLKIT)
endif

SPLC_FLAGS += -t $(SPL_PATH) --rebuild-toolkits

SPL_CMD_ARGS ?= 
SPL_MAIN_COMPOSITE = com.ibm.streamsx.eventstore.sample::InsertSample

build: distributed 

all: clean build

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --output-directory=$(OUTPUT_DIR) $(SPL_CMD_ARGS)
	
clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE)
	rm -rf $(OUTPUT_DIR)
	rm -rf doc

tkidx:
	$(STREAMS_INSTALL)/bin/spl-make-toolkit -i .

configure:
	-streamtool rmappconfig --noprompt eventstore
	streamtool mkappconfig --property databaseName=$(EVENTSTORE_DB) --property connectionString="$(EVENTSTORE_CONNECTION)" --property eventStoreUser=$(EVENTSTORE_USER) --property eventStorePassword=$(EVENTSTORE_PASSWORD) eventstore

configure-store:
	-streamtool rmappconfig --noprompt eventstore
	streamtool mkappconfig --property databaseName=$(EVENTSTORE_DB) --property connectionString="$(EVENTSTORE_CONNECTION)" --property eventStoreUser=$(EVENTSTORE_USER) --property eventStorePassword=$(EVENTSTORE_PASSWORD) --property trustStorePassword=$(EVENTSTORE_TRUSTSTORE_PASSWORD) --property keyStorePassword=$(EVENTSTORE_KEYSTORE_PASSWORD) eventstore

