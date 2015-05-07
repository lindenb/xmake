package com.github.lindenb.xmake;

public interface Variable
	extends Cloneable
	{
	public enum Type {DOM,SYSTEM};
	public String getName();
	public Type getType();
	}
