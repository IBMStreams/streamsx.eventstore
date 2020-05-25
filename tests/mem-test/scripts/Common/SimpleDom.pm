# Copyright (C)2016, International Business Machines Corporation
# All rights reserved.                                                       
#                                                                                          
# contains the following packages
# - SimpleDom	simple read-only DOM implementation, providing a subset of methods
#               uses XML::Parser to read the input xml file
# - SimpleNode	combination of DOM Node and Element interfaces
# - NodeList	holds a list of references to elements, can be filtered

###########################################################
# Class NodeList
# self is an array, so normal perl array indices and slicing can be used
# public methods :
# - lastIndex()		returns the index of the last node in the list
# - size()			returns the number of nodes in the list
# - position(index)	returns a reference to the node at the given index in the list, undef if out-of-bounds
# - filter(list)	returns a new NodeList, containing the original nodes which match the filter condition
# - clear()			clears the NodeList
# - getTexts()		returns an array containg the result of getText() from each node in the set
###########################################################

package NodeList;

use strict;
use warnings;

sub new
{
	my $class = shift;
	my $source = shift;

	my $self = [];
	_copyFrom($self,$source) if (defined $source);

	bless($self,$class);
	return $self;
}

sub _copyFrom
{
	my ($self,$src) = @_;
	foreach my $i (@$src)
	{
		push(@$self,$i);
	}
}

sub clear
{
	my ($self) = shift;
	@$self = ();
}

sub position
{
	my $self = shift;
	my $pos = shift;
	my $nodeRef = undef;
	$nodeRef = @$self[$pos] if ($pos >= 0 && $pos <= $self->lastIndex());
	return $nodeRef;
}

sub lastIndex
{
	my $self = shift;
	return scalar(@$self) - 1;
}

sub size
{
	my $self = shift;
	return scalar(@$self);
}

# -----------------------------------------------------------
# invokes the match function on each node in the list, if it
# returns true the node is added to the result list.
# -----------------------------------------------------------
sub filter
{
	my $self = shift;
	my @filterList = @_;

	my $result = new NodeList();
	foreach my $n (@$self)
	{
		push(@$result,$n) if ($n->match(@filterList));	
	}	
	return $result;
}

sub getTexts
{
	my $self = shift;
	my @result;
	foreach my $n (@$self)
	{
		push(@result,$n->getText());	
	}	
	return @result;
}

###########################################################
# Class SimpleNode
# public methods :
# - getName()		returns the tagname of the element
# - getPath(attr)	returns the path of the element (optional key attribute)
# - hasAttribute(attr)	returns true if the element has the given attribute
# - getAttribute(attr)	returns the value of attribute attr, undef if the element does not have it
# - getAttributes()	returns a hash with the elements attributes (name->value)
# - getAttributeValues(list) returns a list with the values of the given attributes
# - getDefinedAttributeValues(list) returns a list with the values of the given attributes, undef if the attribute is not found
# - getChilds()		returns a NodeList with the childs of the element
# - getChildNames()	returns an array with the names of the childs of this element
# - getChildTexts() returns an array with the getText() result of all child elements
# - getText()		returns normalized content of all text nodes 
# - getFullText()	returns content of all text nodes (including leading/trailing whitespace)
# - getElementsByName(tagname)	returns a NodeList with all descendants of the element with the given name
# - getElementsByPath(path)	returns a NodeList with all elements selected by the given path
# - match(predicates)	returns true if the element matches the given list of conditions
# - dumpTree()		print the subtree of the element	
# - dumpPath(attr)	returns a string with the pathes of all subelements
###########################################################

package SimpleNode;

use strict;
use warnings;
use Data::Dumper;

# ---------------------------------------------------------
# constructor, create a new simple element node, parameters :
# 1: tagname,  2: the level in the xml tree, 3: the path of this element
# 4: a reference to the dom object this node belongs to 
# ---------------------------------------------------------
sub new
{
	my $class = shift;
	my ($elementName,$level,$path,$dom) = (shift,shift,shift,shift);
	unless (defined $dom) { die "abort - SimpleNode new : missing one or more parameters ...\n"; }

	my $self = {
		_elementName => $elementName,
		_level => $level,
		_path => $path,
		_domObject => $dom,
		_attributes => {},
	  	_childs => [],
		_text => "",
		_fullText => "",
	};
	bless($self,$class);
	return $self;
}

# ---------------------------------------------------------
# private methods to change the node
# for adding childs,attributes and text
# ---------------------------------------------------------

sub _addAttribute
{
	my $self = shift;
	my ($name,$value) = (shift,shift);

	unless (defined $value) { die "abort - SimpleNode::addAttribute: missing attribute name and/or value ...\n"; }
	if (exists($self->{_attributes}->{$name})) { die "abort - SimpleNode::addAttribute: attribute $name already exists ...\n"; }

	$self->{_attributes}->{$name} = $value;
}

sub _addChild
{
	my $self = shift;
	my $nodeRef = shift;
	unless (defined $nodeRef) { die "abort - SimpleNode::addChild: missing node to add ...\n"; }

	push(@{$self->{_childs}}, $nodeRef);
}

sub _addText
{
	my $self = shift;
	my $text = shift;

	unless (defined $text) { die "abort - SimpleNode::addText: missing text to add ...\n"; }
	$self->{_fullText} .= $text;
	# trim _text 
	$self->{_text} = $self->{_fullText};
	$self->{_text} =~ s/^\s+|\s+$//g;
}

# ---------------------------------------------------------
# getter methods to retrieve the tagname, text, attributes, childs,  etc.
# ---------------------------------------------------------

sub getName
{
	my $self = shift;
	return $self->{_elementName};
}

sub getPath
{
	my ($self,$attr) = (shift,shift);
	my $pred = "";

	if (defined($attr) && $self->hasAttribute($attr))
	{
		$pred = '[@' . $attr . "=" . $self->getAttribute($attr) . "]";
	}
	return $self->{_path} . "$pred";
}

sub hasAttribute
{
	my ($self,$attrName) = (shift,shift);
	return exists($self->{_attributes}->{$attrName}) ? 1 : 0;
}

sub getAttribute
{
	my ($self,$attrName) = (shift,shift);
	if (exists($self->{_attributes}->{$attrName}))
	{
		return $self->{_attributes}->{$attrName};
	}
	return undef;
}

# returns a hash with the attributes (name -> value) 
sub getAttributes
{
	my $self = shift;	
	return { %{$self->{_attributes}} };
}

# can be given a list of attribute names, returns the values
# if an attribute name in the list is not available, "" is returned for the value
sub getAttributeValues
{
	my $self = shift;
	my @attrs = @_;

	my @result;
	foreach my $a (@attrs)
	{
		if (exists($self->{_attributes}->{$a}))
		{
			push @result , $self->{_attributes}->{$a};
		}
		else
		{
			push @result, "";
		}
	}
	return @result;
}

# can be given a list of attribute names, returns the values
# if an attribute name in the list is not available, undef is returned for the value
sub getDefinedAttributeValues
{
	my $self = shift;
	my @attrs = @_;

	my @result;
	foreach my $a (@attrs)
	{
		if (exists($self->{_attributes}->{$a}))
		{
			push @result , $self->{_attributes}->{$a};
		}
		else
		{
			push @result, undef;
		}
	}
	return @result;
}

sub getChilds
{
	my $self = shift;
	return new NodeList( $self->{_childs} );
}

sub getChildNames
{
	my $self = shift;
	my @names = ();
	foreach my $c (@{$self->{_childs}})
	{
		push(@names,$c->getName());
	}
	return @names;
}

sub getChildTexts
{
	my $self = shift;
	my @texts = ();
	foreach my $c (@{$self->{_childs}})
	{
		push(@texts,$c->getText());
	}
	return @texts;
}

sub getText
{
	my $self = shift;
	return $self->{_text} ;
}

sub getFullText
{
	my $self = shift;
	return $self->{_fullText};
}

# ---------------------------------------------------------
# search nodes by tagname, the whole subtree is searched, "*" matches any tag
# ---------------------------------------------------------
sub _getElementsByName
{
	my ($self,$tagName,$res,$level) = (shift,shift,shift,shift);

	# only add descendants , not self
	if ($level >0)
	{
	  	if ($self->match( $tagName ))
	  	{
			push(@$res,$self);
	  	}
	}

	foreach my $c (@{$self->{_childs}})
	{
		$c->_getElementsByName($tagName,$res,$level+1);
	}
}

sub getElementsByName
{
	my ($self,$tagname) = (shift,shift);
	my $result = new NodeList();
	$self->_getElementsByName($tagname,$result,0);
	return $result;
}

# ---------------------------------------------------------
# search nodes with simplified xpath syntax, allowed constructs :
# / // . * [] @name @name=value
# ---------------------------------------------------------
sub getElementsByPath
{
	my ($self,$path) = (shift,shift);
	my $origPath = $path;

	# the // abbreviation means /descendant:: , as / is also used as location step separator
	# this makes it hard to parse, so replace // with /+ for easier handling (+ is not allowed in qnames)
	$path =~ s/\/\//\/\+/g;

	# check if the path is absolute (starts with "/")
	my $isAbsolute = 0;
	if ($path =~ /^\//)
	{
		$isAbsolute = 1;
		$path = substr($path,1);
	}

	# parse the locationsteps
	my @lsteps;
	my @steps = split("/",$path);
	foreach my $st (@steps)
	{
		# ignore "." which is self::
		next if ($st =~ /^\.$/); 

		my $locstep = {};

		# default axis is child::
		$locstep->{axis} = 0;
        if ($st =~ /^\+/)
        {
			# axis is descendant::
			$locstep->{axis} = 1;
			$st = substr($st,1);
		}

		# nodetest 
        if ($st =~ /^(\*|[A-Za-z0-9_-]+)/)
        {
			$locstep->{nodeTest} = $1;
		}

		# predicates
        if ($st =~ /\[(.+)\]$/)
        {
			$locstep->{predicates} = $1;
		}

		unless (exists($locstep->{nodeTest})) { die "abort - SimpleNode::getElementsByPath: nodeTest not found in path expression '$origPath'...\n"; }
		push(@lsteps,$locstep);
	}

	# start node is self or the root node in case of an absolute path
	my $nodes = new NodeList();
	if ($isAbsolute)
	{
		push(@$nodes,$self->{_domObject}->{_searchRoot});
	}
	else
	{
		push(@$nodes,$self);
	}

	# process the location steps
	foreach my $step (@lsteps)
	{
		# stop if nodes list is empty 
		last if (scalar(@$nodes) == 0);
	
		my $result = new NodeList();
		foreach my $old (@$nodes)
		{
			# get childs
			push(@$result, @{$old->getChilds()}) if ($step->{axis} == 0);
			# get descendants
			push(@$result, @{$old->getElementsByName($step->{nodeTest})}) if ($step->{axis} == 1);		
		}

		# apply nodetest (only needed for the child case)
		$result = $result->filter($step->{nodeTest}) if ($step->{axis} == 0);

        # filter results for predicates if any, special handling for [num]
		if (exists($step->{predicates}) && $step->{predicates} ne "")
		{
			# better: allow [][] syntax for preds instead of [,,]
			my @preds = split(",",$step->{predicates});
			# better: move the number predicate handling to NodeList::filter		
			if ($preds[0] =~ /^(\d+)$/)
			{
				my $n = $result->position($1);
				$result->clear();
				push(@$result,$n) if (defined($n));
			}
			else
			{
				$result = $result->filter(@preds);
			}
		}

        # swap old set with new set
		$nodes = $result;
	}

	return $nodes;
}

# ---------------------------------------------------------
# match the node against a list of predicates (parameters 2..n)
# to match the tagname use "*" or "xxx" , to check for existence of an attribute
# use '@attr', to check for an attribute value use '@attr=val'  
# ---------------------------------------------------------
sub match
{
	my $self = shift;
	my @predicates = @_;

	# test each item in the list, return 0 if test fails
	foreach my $p (@predicates)
	{
		# a single * matches all tagnames
		next if ($p eq '*');

		# a string like "@attr" will match if the element has an attribute named "attr"
		if ($p =~ /^@(\w+)$/)
		{
			return 0 if (! ($self->hasAttribute($1)));
			next;
		}

		# a string like "@attr=val" will match if the element has an attribute named "attr"
		# and the value of this attribute is "val"
		if ($p =~ /^@(\w+)=(\w+)$/)
		{
			return 0 if (! ($self->hasAttribute($1)));
			return 0 if (! ($self->getAttribute($1) eq $2));
			next;
		}
	
		# any other string is matched against the tagname
		return 0 if (! ($p eq $self->{_elementName}))
	}

	# all tests passed
	return 1;
}

# ---------------------------------------------------------
# methods to dump the subtree of this node 
# ---------------------------------------------------------

sub dumpTree
{
	my $self = shift;

	my $indent = pack ( "A" x ($self->{_level} * 4) , " ");
	print "${indent}element " . $self->{_elementName} . " { \n";
	print "${indent}  path: " . $self->{_path} . "\n";
	print "${indent}  text: " . $self->{_text} . "\n";

	foreach my $a (keys  %{$self->{_attributes}})
	{
	  	print "${indent}  attr: ${a}=" . $self->{_attributes}->{$a} . "\n";
	}
	foreach my $c (@{$self->{_childs}})
	{
		$c->dumpTree();
	}
	print "${indent}}\n";
}

sub _dumpPath
{
	my ($self,$attrName,$str) = (shift,shift,shift);
	my $s = $self->getPath($attrName);
	$$str .= $s . "\n";
	foreach my $c (@{$self->{_childs}})
	{
		$c->_dumpPath($attrName,$str);
	}
}

sub dumpPath
{
	my ($self,$attrName) = (shift,shift);
	my $result = "";
	$self->_dumpPath($attrName,\$result);
	return $result;
}

#######################################################################
# Class SimpleDom
# public methods :
# - SimpleDom(string xmlfile)	constructor, creates a new dom from the xmlfile given as parameter
# - dumpParserTree()			returns a string containing the output from XML::Parser for the source xml file
# - getRoot()					returns a reference to the root node
# - dumpTree()					invokes dumpTree() on the root node
# - dumpPath(string attribute)		invokes dumpPath() on the root node
# - getElementsByName(string name)	invokes getElementsByName() on the root node
# - getElementsByPath(string path)	invokes getElementsByPath() on the root node
#######################################################################

package SimpleDom;

use strict;
use warnings;
use XML::Parser;
use Data::Dumper;

# ---------------------------------------------------------------------
# constructor, creates a new DOM object from the given xml file
# ---------------------------------------------------------------------
sub new
{
	my $class = shift;
	my $xmlFile = shift;
	unless (defined $xmlFile) { die "abort - missing filename ...\n"; }

	# invoke XML::Parser to get the list representation of the xml file
	my @data; 
	my $parser = new XML::Parser(Style => 'Tree');
	@data = $parser->parsefile($xmlFile);
	my $size = @data;
	unless ($size == 1) { die "abort - more than one root element \n"; }

	my $self = {
		_xmlFilename => $xmlFile,
		_data => \@data,
	};

	# build the dom recursively
	my $root = _buildNode($self,0,"",$data[0]);
	$self->{_rootNode} = $root;

	# add node for path search
	my $searchRoot = new SimpleNode("/",-1,"/",$self);
	$searchRoot->_addChild($root);
	$self->{_searchRoot} = $searchRoot;

	bless($self,$class);
	return $self;
}

# ---------------------------------------------------------------------
# build the DOM from the xml::parser output
# parameters: 1: reference to the dom object, 2: the level in the xml tree
# 3: the path of the parent node,  4: the data list for this node 
# ---------------------------------------------------------------------
sub _buildNode
{
	my ($self,$level,$path,$nodeList) = @_;

	# the list must have two entries
	my $size = @$nodeList;
	if ($size != 2) { die "abort - error in list structure from xml::parser \n"; }

	# first entry is the element name
	my $name = @$nodeList[0];
	$path .= "/$name";

	# second is the content of the element (also a list)
	my $content = @$nodeList[1];

	# at least an empty hash for the attributes must be in the content list
	my $contentSize = @$content;
	if ($contentSize < 1 or $contentSize % 2 == 0) { die "abort - error in list structure from xml::parser \n"; }  
  
	# create a new node  
	my $n = new SimpleNode($name,$level,$path,$self);

	# get attributes, and add them to the new node
	my %attrs = %{@{$content}[0]} ;
	foreach my $a (keys %attrs)
	{
		$n->_addAttribute($a,$attrs{$a});
	}

	# process childs
	my $i = 1;
	while ($i < $contentSize)
	{
		# if the element name is "0", it is a text node
		if (@{$content}[$i] eq "0")
		{
			$n->_addText(@{$content}[$i+1]);
		}
		# otherwise it is a child element
		else
		{
			my @arr = @{$content}[$i,$i+1] ;
		  	$n->_addChild(_buildNode($self, $level+1, $path, \@arr ));	
		}
		$i += 2;
	}

	# return the new node
	return $n;
};

# ---------------------------------------------------------------------
# return a string containng the output of XML::Parser
# ---------------------------------------------------------------------
sub dumpParserTree
{
	my $self = shift;
	return Dumper( $self->{_data} );
}

# ---------------------------------------------------------------------
# return reference to the root node
# ---------------------------------------------------------------------
sub getRoot
{
	my $self = shift;
	return $self->{_searchRoot};
}

# ---------------------------------------------------------------------
# the remaining functions are forwarded to the root node
# ---------------------------------------------------------------------
sub dumpTree
{
	my $self = shift;
	$self->{_rootNode}->dumpTree();
}

sub dumpPath
{
	my ($self,$attrName) = (shift,shift);
	return $self->{_rootNode}->dumpPath($attrName);
}

sub getElementsByName
{
	my ($self,$name) = (shift,shift);
	return $self->{_searchRoot}->getElementsByName($name);
}

sub getElementsByPath
{
	my ($self,$pathExpr) = (shift,shift);
	return $self->{_searchRoot}->getElementsByPath($pathExpr);
}

1;

