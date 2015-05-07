package com.github.lindenb.xmake;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

public class DefaultVariable
	implements Variable
	{
	private String name;
	private List<Node> elements = new ArrayList<>();
	public DefaultVariable(String name)
		{
		this.name=name;
		}
	public DefaultVariable(String name,Node root)
		{
		this(name);
		this.elements.add(root);
		}
	public DefaultVariable(String name, List<Node> elements)
		{
		this(name);
		this.elements.addAll(elements);
		}
	
	@Override
	public Type getType() {
		return Type.DOM;
		}
	
	@Override
	public String getName()
		{
		return name;
		}
	
	public List<Node> nodes()
		{
		return this.elements;
		}	
	@Override
	public String toString() {
		return getName()+" N(nodes)="+this.elements.size();
		}
	
	@Override
	public Variable clone(){
		return new DefaultVariable(getName(),this.elements);
		}
	}
