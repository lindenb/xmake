package com.github.lindenb.xmake;

import java.util.HashMap;
import java.util.Map;

public class DefaultContext implements Context
	{
	private Context parent=null;
	private Map<String, Variable> variables = new HashMap<String, Variable>();
	
	DefaultContext(Context parent)
		{
		this.parent = parent;
		}
	
	DefaultContext()
		{
		this(null);
		}
	
	
	
	@Override
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

	
	@Override
	public String toString()
		{ 
		return variables.toString();
		}
	}
