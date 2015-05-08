package com.github.lindenb.xmake;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Context
	{
	private Context parent=null;
	private Map<String, Variable> variables = new HashMap<String, Variable>();
	
	Context(Context parent)
		{
		this.parent = parent;
		}
	
	Context()
		{
		this(null);
		}
	
	public Variable get(String name)
		{
		Variable v= variables.get(name);
		if(v==null)
			{
			if(parent!=null)
				{
				return parent.get(name);
				}
			else
				{
				String s= System.getProperty(name);
				if(s==null ) s = System.getenv(name);
				if(s!=null)
					{
					v=new SystemVariable(name, s);
					put(v);
					}
				}
			}
		return v;
		}
	
	public void put(Variable variable)
		{
		this.variables.put(
			variable.getName(),
			variable
			);
		}
	
	public Set<String> keySet()
		{
		Set<String> hash=new HashSet<>();
		Context c=this;
		while(c!=null)
			{
			hash.addAll(c.variables.keySet());
			c=c.parent;
			}
		return hash;
		}
		
	@Override
	public String toString()
		{ 
		return keySet().toString();
		}
	}
