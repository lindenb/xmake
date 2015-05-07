package com.github.lindenb.xmake;

import com.github.lindenb.xmake.Variable.Type;

public class SystemVariable implements Variable
	{
	private String name;
	private String value;
	public SystemVariable(String name)
		{
		this(name,"");
		}
	
	@Override
	public Type getType() {
		return Type.SYSTEM;
		}

	
	public SystemVariable(String name,String value)
		{
		this.name=name;
		this.value=value;
		}
	@Override
	public String getName() {
		return name;
		}
	
	public String getValue() {
		return value;
		}
	
	@Override
	protected Variable clone(){
		return new SystemVariable(name,value);
		}
	}
