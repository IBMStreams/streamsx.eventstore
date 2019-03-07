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

This toolkit contains operators that enable you to connect IBM Streams to IBM Db2 Event Store.

Currently, this toolkit contains one operator: a file sink operator called EventStoreSink for inserting IBM Streams tuples in to an IBM Db2 Event Store table.

To use this operator, you must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

You can find precompiled EventStoreSink toolkits for various IBM Db2 Event Store RELEASES here:
[Event Store Releases](https://github.com/IBMStreams/streamsx.eventstore/releases)



This version of the toolkit is intended for use with IBM Streams release 4.2 and later.
