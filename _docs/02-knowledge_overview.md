---
title: "Toolkit technical background overview"
permalink: /docs/knowledge/overview/
excerpt: "Basic knowledge of the toolkits technical domain."
last_modified_at: 2018-11-20T12:37:48-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "knowledgedocs"
---
{% include toc %}
{% include editme %}

This toolkit contains operators that enable you to connect IBM Streams to [IBM Db2 Event Store](https://www.ibm.com/products/db2-event-store).

IBM Db2 Event Store is an in-memory database designed to rapidly ingest and analyze streamed data in event-driven applications. It provides the fabric for fast data with its ability to process massive volume of events in real-time, coupled with optimization for streamed data performance for advanced analytics and actionable insights.

Currently, this toolkit contains one operator: an operator called EventStoreSink for inserting IBM Streams tuples in to an IBM Db2 Event Store table.

To use this toolkit, you must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

You can find precompiled EventStoreSink toolkits for various IBM Db2 Event Store RELEASES here:
[Event Store Releases](https://github.com/IBMStreams/streamsx.eventstore/releases)

## Supported versions

- **IBM Streams:** 4.2.0 or later
- **IBM Db2 Event Store:** 1.1.0
