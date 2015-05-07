package com.github.lindenb.xmake;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

public class DefaultVariable
	implements Variable
	{
	private String name;
	private Context contextAtDefinition;
	private List<Node> elements = new ArrayList<>();
	public DefaultVariable(Context ctx,String name)
		{
		this.contextAtDefinition=ctx;
		this.name=name;
		}
	public DefaultVariable(Context ctx,String name,Node root)
		{
		this(ctx,name);
		this.elements.add(root);
		}
	public DefaultVariable(Context ctx,String name, List<Node> elements)
		{
		this(ctx,name);
		this.elements.addAll(elements);
		}
	
	public Context getContext()
		{
		return contextAtDefinition;
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
		return new DefaultVariable(this.contextAtDefinition,getName(),this.elements);
		}
	}
